package edu.northeastern.courseassistant.actors;

import edu.northeastern.courseassistant.model.Course;
import edu.northeastern.courseassistant.protocol.ConversationProtocol;
import edu.northeastern.courseassistant.protocol.RAGProtocol;
import edu.northeastern.courseassistant.services.RAGService;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * RAG (Retrieval Augmented Generation) Actor
 *
 * This actor is responsible for:
 * 1. Loading and managing the course database
 * 2. Performing search operations to find relevant courses
 * 3. Handling course data refreshes
 */
public class RAGActor extends AbstractBehavior<RAGProtocol.Command> {

    private final RAGService ragService;
    private List<Course> courseDatabase = new ArrayList<>();

    private RAGActor(ActorContext<RAGProtocol.Command> context, RAGService ragService) {
        super(context);
        this.ragService = ragService;

        // Load the course database
        loadCourseDatabase();
    }

    public static Behavior<RAGProtocol.Command> create(RAGService ragService) {
        return Behaviors.setup(context -> new RAGActor(context, ragService));
    }

    @Override
    public Receive<RAGProtocol.Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(RAGProtocol.SearchCourses.class, this::onSearchCourses)
                .onMessage(RAGProtocol.RefreshCourseData.class, this::onRefreshCourseData)
                .build();
    }

    /**
     * Handles the search courses command
     */
    private Behavior<RAGProtocol.Command> onSearchCourses(RAGProtocol.SearchCourses command) {
        getContext().getLog().info("Searching courses for query: {}", command.getQuery());

        try {
            // Perform the search using the RAG service
            List<Course> relevantCourses = ragService.enhancedSearch(command.getQuery());

            getContext().getLog().info("Found {} relevant courses for query: {}",
                    relevantCourses.size(), command.getQuery());

            // Send the results back to the conversation manager
            command.getReplyTo().tell(new ConversationProtocol.RAGResultsReceived(
                    command.getQuery(),
                    relevantCourses,
                    command.getRequestId()));

        } catch (Exception e) {
            getContext().getLog().error("Error searching courses", e);

            // Send empty results in case of error
            command.getReplyTo().tell(new ConversationProtocol.RAGResultsReceived(
                    command.getQuery(),
                    new ArrayList<>(),
                    command.getRequestId()));
        }

        return this;
    }

    /**
     * Handles the refresh course data command
     */
    private Behavior<RAGProtocol.Command> onRefreshCourseData(RAGProtocol.RefreshCourseData command) {
        getContext().getLog().info("Refreshing course data");

        try {
            // Reload the course database
            boolean success = loadCourseDatabase();

            String message = success
                    ? "Course data refreshed successfully"
                    : "Failed to refresh course data";

            // Send the response
            command.getReplyTo().tell(new RAGProtocol.CourseDataRefreshed(success, message));

        } catch (Exception e) {
            getContext().getLog().error("Error refreshing course data", e);

            // Send failure response
            command.getReplyTo().tell(
                    new RAGProtocol.CourseDataRefreshed(false, "Error: " + e.getMessage()));
        }

        return this;
    }

    /**
     * Loads the course database from the JSON file
     * @return true if successful, false otherwise
     */
    private boolean loadCourseDatabase() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            courseDatabase = objectMapper.readValue(
                    new File("src/main/resources/courses.json"),
                    new TypeReference<List<Course>>() {});

            getContext().getLog().info("Loaded {} courses from database", courseDatabase.size());
            return true;

        } catch (IOException e) {
            getContext().getLog().error("Failed to load course database", e);
            return false;
        }
    }

    /**
     * Factory for creating the RAG actor
     */
    public static class Factory {
        private final RAGService ragService;

        public Factory(RAGService ragService) {
            this.ragService = ragService;
        }

        public Behavior<RAGProtocol.Command> create() {
            return RAGActor.create(ragService);
        }
    }
}