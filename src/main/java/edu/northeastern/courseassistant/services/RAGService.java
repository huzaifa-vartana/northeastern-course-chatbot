package edu.northeastern.courseassistant.services;

import edu.northeastern.courseassistant.model.Course;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Implementation of the Retrieval Augmented Generation (RAG) service using Qdrant
 * This service is responsible for:
 * 1. Loading course data
 * 2. Creating embeddings for course information (one-time)
 * 3. Storing embeddings in Qdrant
 * 4. Searching for relevant courses based on user queries
 */
public class RAGService {
    private static final int EMBEDDING_DIMENSION = 1536; // Dimension for OpenAI's ada-002 model
    private static final int MAX_RESULTS = 10;
    private static final String COLLECTION_NAME = "neu_courses";

    private final List<Course> courseDatabase;
    private final OpenAiService openAiService;
    private final QdrantClient qdrantClient;
    private final Map<String, Course> courseIdMap = new HashMap<>();

    /**
     * Constructor - initializes the service and loads course data
     * @param openAiApiKey API key for OpenAI
     */
    public RAGService(String openAiApiKey) {
        this.openAiService = new OpenAiService(openAiApiKey);
        this.courseDatabase = loadCourses();

        // Initialize Qdrant client
        this.qdrantClient = new QdrantClient(
                QdrantGrpcClient.newBuilder("localhost", 6334, false).build());

        // Initialize the vector database
        initializeVectorDatabase();
    }

    /**
     * Loads course data from the JSON file
     */
    private List<Course> loadCourses() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(
                    new File("src/main/resources/courses.json"),
                    new TypeReference<List<Course>>() {});
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Initializes the Qdrant vector database with course embeddings
     */
    private void initializeVectorDatabase() {
        try {
            // Create a collection if it doesn't exist
            try {
                qdrantClient.getCollectionInfoAsync(COLLECTION_NAME).get();
                System.out.println("Collection already exists, skipping creation");
            } catch (Exception e) {
                System.out.println("Creating new collection: " + COLLECTION_NAME);
                qdrantClient.createCollectionAsync(
                        COLLECTION_NAME,
                        VectorParams.newBuilder()
                                .setSize(EMBEDDING_DIMENSION)
                                .setDistance(Distance.Cosine)
                                .build()
                ).get();
            }

            // Check if we need to populate the collection
            long pointCount = qdrantClient.getCollectionInfoAsync(COLLECTION_NAME).get().getPointsCount();
            if (pointCount >= courseDatabase.size()) {
                System.out.println("Collection already populated with " + pointCount + " points");
                // Build the course ID map for quick lookup
                for (Course course : courseDatabase) {
                    courseIdMap.put(course.getCrn(), course);
                }
                return;
            }

            // Populate collection with course embeddings
            System.out.println("Populating collection with " + courseDatabase.size() + " courses");
            List<PointStruct> points = new ArrayList<>();

            for (Course course : courseDatabase) {
                String courseId = course.getCrn();
                courseIdMap.put(courseId, course);

                // Create text representation of the course for embedding
                String courseText = createCourseText(course);

                // Get embedding from OpenAI (only done once per course)
                float[] embedding = getEmbedding(courseText);

                // Build the point with metadata
                PointStruct point = PointStruct.newBuilder()
                        .setId(id(Long.parseLong(courseId)))
                        .setVectors(vectors(embedding))
                        .putAllPayload(Map.of(
                                "crn", value(course.getCrn()),
                                "title", value(course.getTitle()),
                                "subject", value(course.getSubject()),
                                "number", value(course.getNumber()),
                                "instructor", value(course.getInstructor()),
                                "credits", value(course.getCredits())
                        ))
                        .build();

                points.add(point);
            }

            // Insert all points into Qdrant
            qdrantClient.upsertAsync(COLLECTION_NAME, points).get();
            System.out.println("Successfully added " + points.size() + " points to Qdrant");

        } catch (Exception e) {
            System.err.println("Error initializing vector database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates a text representation of a course for embedding
     */
    private String createCourseText(Course course) {
        return String.format("%s %s-%s: %s. Instructor: %s. Schedule: %s %s in %s. Campus: %s. Credits: %s.",
                course.getSubject(),
                course.getNumber(),
                course.getSection(),
                course.getTitle(),
                course.getInstructor(),
                course.getDays(),
                course.getTime(),
                course.getLocation(),
                course.getCampus(),
                course.getCredits());
    }

    /**
     * Gets embedding for text using OpenAI API
     */
    private float[] getEmbedding(String text) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model("text-embedding-ada-002")
                .input(Collections.singletonList(text))
                .build();

        try {
            EmbeddingResult result = openAiService.createEmbeddings(request);
            List<Double> embedding = result.getData().get(0).getEmbedding();

            // Convert List<Double> to float[]
            float[] embeddingArray = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                embeddingArray[i] = embedding.get(i).floatValue();
            }

            return embeddingArray;
        } catch (Exception e) {
            e.printStackTrace();
            // Return a zero vector if there's an error
            return new float[EMBEDDING_DIMENSION];
        }
    }

    /**
     * Searches for courses relevant to the user query using Qdrant
     * @param query The user's natural language query
     * @return List of relevant courses
     */
    public List<Course> searchCourses(String query) {
        // Get embedding for the query
        float[] queryEmbedding = getEmbedding(query);

        try {
            // Search for similar vectors in Qdrant
            List<ScoredPoint> results = qdrantClient.searchAsync(
                    SearchPoints.newBuilder()
                            .setCollectionName(COLLECTION_NAME)
                            .addAllVector(convertToList(queryEmbedding))
                            .setLimit(MAX_RESULTS)
                            .build()
            ).get();

            // Convert results to Course objects
            return results.stream()
                    .map(point -> {
                        long numericId = point.getId().getNum();
                        String crnId = String.valueOf(numericId);
                        return courseIdMap.get(crnId);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error searching in Qdrant: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Converts float array to List<Float>
     */
    private List<Float> convertToList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float value : array) {
            list.add(value);
        }
        return list;
    }

    /**
     * Combines keyword search with semantic search for better results
     * @param query The user's natural language query
     * @return List of relevant courses
     */
    public List<Course> enhancedSearch(String query) {
        // Get semantic search results
        List<Course> semanticResults = searchCourses(query);

        // Perform additional keyword search
        List<Course> keywordResults = keywordSearch(query);

        // Combine results (removing duplicates)
        Set<String> addedCrns = new HashSet<>();
        List<Course> combinedResults = new ArrayList<>();

        // First add semantic results
        for (Course course : semanticResults) {
            combinedResults.add(course);
            addedCrns.add(course.getCrn());
        }

        // Then add keyword results that aren't already included
        for (Course course : keywordResults) {
            if (!addedCrns.contains(course.getCrn())) {
                combinedResults.add(course);
                addedCrns.add(course.getCrn());
            }
        }

        return combinedResults;
    }

    /**
     * Simple keyword search on course data
     */
    private List<Course> keywordSearch(String query) {
        String[] keywords = query.toLowerCase().split("\\s+");

        return courseDatabase.stream()
                .filter(course -> matchesKeywords(course, keywords))
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a course matches the given keywords
     */
    private boolean matchesKeywords(Course course, String[] keywords) {
        String courseText = createCourseText(course).toLowerCase();

        for (String keyword : keywords) {
            if (courseText.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Closes the Qdrant client connection
     */
    public void close() {
        if (qdrantClient != null) {
            try {
                qdrantClient.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}