package edu.northeastern.courseassistant.services;

import edu.northeastern.courseassistant.model.Course;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of the Retrieval Augmented Generation (RAG) service
 * This service is responsible for:
 * 1. Loading course data
 * 2. Creating embeddings for course information
 * 3. Searching for relevant courses based on user queries
 *
 * This is a simplified implementation that does not depend on external vector database libraries
 */
public class RAGService {
    private static final int EMBEDDING_DIMENSION = 1536; // Dimension for OpenAI's ada-002 model
    private static final int MAX_RESULTS = 10;

    private final List<Course> courseDatabase;
    private final OpenAiService openAiService;

    // Internal storage for course embeddings
    private final Map<String, float[]> courseEmbeddings = new HashMap<>();

    /**
     * Constructor - initializes the service and loads course data
     * @param openAiApiKey API key for OpenAI
     */
    public RAGService(String openAiApiKey) {
        // Set a reasonable timeout for API calls
        this.openAiService = new OpenAiService(openAiApiKey, 30);
        this.courseDatabase = loadCourses();

        // Only initialize embeddings if API key is valid
        if (openAiApiKey != null && !openAiApiKey.trim().isEmpty()
                && !openAiApiKey.equals("your_openai_api_key_here")) {
            initializeEmbeddings();
        } else {
            System.err.println("WARNING: OpenAI API key is not set or is invalid. " +
                    "Embeddings will not be initialized.");
        }
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
     * Initializes embeddings for all courses
     */
    private void initializeEmbeddings() {
        for (Course course : courseDatabase) {
            String courseId = course.getCrn();
            String courseText = createCourseText(course);

            // Get embedding from OpenAI
            float[] embedding = getEmbedding(courseText);

            // Store in memory
            courseEmbeddings.put(courseId, embedding);
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
        } catch (retrofit2.adapter.rxjava2.HttpException e) {
            if (e.code() == 401) {
                System.err.println("ERROR: Authentication failed with OpenAI API. " +
                        "Please check your API key.");
            } else {
                System.err.println("ERROR: HTTP error " + e.code() + " when calling OpenAI API: " +
                        e.getMessage());
            }
            e.printStackTrace();
            // Return a zero vector if there's an error
            return new float[EMBEDDING_DIMENSION];
        } catch (Exception e) {
            System.err.println("ERROR: Unexpected error when calling OpenAI API: " + e.getMessage());
            e.printStackTrace();
            // Return a zero vector if there's an error
            return new float[EMBEDDING_DIMENSION];
        }
    }

    /**
     * Computes cosine similarity between two vectors
     */
    private float cosineSimilarity(float[] vector1, float[] vector2) {
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }

        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;

        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }

        if (norm1 <= 0 || norm2 <= 0) return 0;

        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Searches for courses relevant to the user query
     * @param query The user's natural language query
     * @return List of relevant courses
     */
    public List<Course> searchCourses(String query) {
        // Get embedding for the query
        float[] queryEmbedding = getEmbedding(query);

        // Calculate similarity for each course
        List<Map.Entry<String, Float>> similarities = new ArrayList<>();

        for (Map.Entry<String, float[]> entry : courseEmbeddings.entrySet()) {
            String courseId = entry.getKey();
            float[] courseEmbedding = entry.getValue();

            float similarity = cosineSimilarity(queryEmbedding, courseEmbedding);
            similarities.add(new AbstractMap.SimpleEntry<>(courseId, similarity));
        }

        // Sort by similarity (descending)
        similarities.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

        // Get top courses
        List<Course> results = new ArrayList<>();

        for (int i = 0; i < Math.min(MAX_RESULTS, similarities.size()); i++) {
            String courseId = similarities.get(i).getKey();
            Course course = findCourseByCrn(courseId);
            if (course != null) {
                results.add(course);
            }
        }

        return results;
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
     * Finds a course by its CRN
     */
    private Course findCourseByCrn(String crn) {
        for (Course course : courseDatabase) {
            if (course.getCrn().equals(crn)) {
                return course;
            }
        }
        return null;
    }
}