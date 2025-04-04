package edu.northeastern.courseassistant.actors;

import edu.northeastern.courseassistant.model.Course;
import edu.northeastern.courseassistant.protocol.ConversationProtocol;
import edu.northeastern.courseassistant.protocol.LLMProtocol;
import edu.northeastern.courseassistant.services.LLMService;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * LLM (Large Language Model) Actor
 *
 * This actor is responsible for:
 * 1. Processing user queries using the LLM service
 * 2. Generating natural language responses
 * 3. Identifying courses that should be highlighted in the response
 */
public class LLMActor extends AbstractBehavior<LLMProtocol.Command> {

    private final LLMService llmService;

    private LLMActor(ActorContext<LLMProtocol.Command> context, LLMService llmService) {
        super(context);
        this.llmService = llmService;
    }

    public static Behavior<LLMProtocol.Command> create(LLMService llmService) {
        return Behaviors.setup(context -> new LLMActor(context, llmService));
    }

    @Override
    public Receive<LLMProtocol.Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(LLMProtocol.GenerateResponse.class, this::onGenerateResponse)
                .build();
    }

    /**
     * Handles the generate response command
     */
    private Behavior<LLMProtocol.Command> onGenerateResponse(LLMProtocol.GenerateResponse command) {
        getContext().getLog().info("Generating response for query: {}", command.getUserQuery());

        try {
            // Generate response using the LLM service
            String response = generateResponse(
                    command.getUserQuery(),
                    command.getContextCourses(),
                    command.getConversationHistory(),
                    command.getUserPreferences());

            // Extract highlighted courses
            List<Course> highlightedCourses = llmService.extractHighlightedCourses(
                    response, command.getContextCourses());

            getContext().getLog().info("Generated response with {} highlighted courses",
                    highlightedCourses.size());

            // Send the response back to the conversation manager
            command.getReplyTo().tell(new ConversationProtocol.LLMResponseReceived(
                    response,
                    highlightedCourses,
                    command.getRequestId()));

        } catch (Exception e) {
            getContext().getLog().error("Error generating response", e);

            // Send error response
            String errorResponse = "I'm sorry, I encountered an error while processing your request. " +
                    "Please try again or rephrase your question.";

            command.getReplyTo().tell(new ConversationProtocol.LLMResponseReceived(
                    errorResponse,
                    List.of(),
                    command.getRequestId()));
        }

        return this;
    }

    /**
     * Generates a response using the LLM service
     * Uses a separate method to allow for mocking in tests
     */
    protected String generateResponse(String userQuery, List<Course> contextCourses,
                                      List<String> conversationHistory, Map<String, String> userPreferences) {
        // For courses with special handling
        if (isCourseInquiryWithNoResults(userQuery, contextCourses)) {
            return handleEmptyCourseResults(userQuery);
        }

        // For general course information
        if (contextCourses.size() == 1) {
            return generateSingleCourseResponse(userQuery, contextCourses.get(0), conversationHistory);
        }

        // Standard response generation
        return llmService.generateResponse(userQuery, contextCourses, conversationHistory);
    }

    /**
     * Checks if the query is about courses but no results were found
     */
    private boolean isCourseInquiryWithNoResults(String query, List<Course> courses) {
        String lowerQuery = query.toLowerCase();
        boolean isCourseQuery = lowerQuery.contains("course") ||
                lowerQuery.contains("class") ||
                lowerQuery.contains("subject") ||
                lowerQuery.matches(".*\\b[A-Za-z]{2,4}\\s?\\d{4}\\b.*");

        return isCourseQuery && courses.isEmpty();
    }

    /**
     * Handles the case where no courses were found for a course inquiry
     */
    private String handleEmptyCourseResults(String query) {
        return "I'm sorry, I couldn't find any courses matching your query: \"" + query + "\". " +
                "Could you try rephrasing or provide more details? You can search by course name, " +
                "subject code (like CSYE or CS), course number, instructor name, or describe the " +
                "topic you're interested in.";
    }

    /**
     * Generates a detailed response for a single course
     */
    private String generateSingleCourseResponse(String query, Course course, List<String> history) {
        // If this is a follow-up question about a specific course aspect
        if (history.size() >= 2) {
            String lastAssistantResponse = history.get(history.size() - 1);
            if (lastAssistantResponse.contains(course.getSubject() + " " + course.getNumber())) {
                // This is likely a follow-up, so use the LLM for a more natural response
                return llmService.generateResponse(query, List.of(course), history);
            }
        }

        // Standard single course response
        StringBuilder response = new StringBuilder();
        response.append("I found the course you're looking for: ")
                .append(course.getSubject()).append(" ").append(course.getNumber())
                .append("-").append(course.getSection()).append(" (").append(course.getTitle()).append(").\n\n");

        response.append("This ").append(course.getCredits()).append("-credit course is taught by ")
                .append(course.getInstructor()).append(" on ")
                .append(course.getReadableDays()).append(" at ").append(course.getTime())
                .append(" in ").append(course.getLocation()).append(" (").append(course.getCampus()).append(" campus).\n\n");

        response.append("Current enrollment: ").append(course.getEnrolled()).append("/")
                .append(course.getCapacity()).append(" students");

        if (course.isAvailable()) {
            response.append(" (").append(course.getAvailable()).append(" seats still available).");
        } else {
            response.append(" (This course is currently full).");
        }

        response.append("\n\nIs there anything specific about this course you'd like to know?");

        return response.toString();
    }

    /**
     * Factory for creating the LLM actor
     */
    public static class Factory {
        private final LLMService llmService;

        public Factory(LLMService llmService) {
            this.llmService = llmService;
        }

        public Behavior<LLMProtocol.Command> create() {
            return LLMActor.create(llmService);
        }
    }
}