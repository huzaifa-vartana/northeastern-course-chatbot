package edu.northeastern.courseassistant.protocol;


import akka.actor.typed.ActorRef;
import edu.northeastern.courseassistant.model.Course;

import java.util.List;


/**
 * Protocol for Conversation Manager Actor
 */
public class ConversationProtocol {
    private ConversationProtocol() {
    } // Prevent instantiation

    /**
     * Base interface for all conversation commands
     */
    public interface Command {
    }

    /**
     * Base interface for all conversation responses
     */
    public interface Response {
    }

    /**
     * Command to process a user message
     */
    public static final class ProcessUserMessage implements Command {
        private final String userMessage;
        private final String requestId;
        private final ActorRef<Response> replyTo;

        public ProcessUserMessage(String userMessage, String requestId, ActorRef<Response> replyTo) {
            this.userMessage = userMessage;
            this.requestId = requestId;
            this.replyTo = replyTo;
        }

        public String getUserMessage() {
            return userMessage;
        }

        public String getRequestId() {
            return requestId;
        }

        public ActorRef<Response> getReplyTo() {
            return replyTo;
        }
    }

    /**
     * Command indicating RAG results have been received
     */
    public static final class RAGResultsReceived implements Command {
        private final String userMessage;
        private final List<Course> relevantCourses;
        private final String requestId;

        public RAGResultsReceived(String userMessage, List<Course> relevantCourses, String requestId) {
            this.userMessage = userMessage;
            this.relevantCourses = relevantCourses;
            this.requestId = requestId;
        }

        public String getUserMessage() {
            return userMessage;
        }

        public List<Course> getRelevantCourses() {
            return relevantCourses;
        }

        public String getRequestId() {
            return requestId;
        }
    }

    /**
     * Command indicating LLM response has been received
     */
    public static final class LLMResponseReceived implements Command {
        private final String response;
        private final List<Course> highlightedCourses;
        private final String requestId;

        public LLMResponseReceived(String response, List<Course> highlightedCourses, String requestId) {
            this.response = response;
            this.highlightedCourses = highlightedCourses;
            this.requestId = requestId;
        }

        public String getResponse() {
            return response;
        }

        public List<Course> getHighlightedCourses() {
            return highlightedCourses;
        }

        public String getRequestId() {
            return requestId;
        }
    }

    /**
     * Command to set a user preference
     */
    public static final class SetPreference implements Command {
        private final String key;
        private final String value;
        private final ActorRef<Response> replyTo;

        public SetPreference(String key, String value, ActorRef<Response> replyTo) {
            this.key = key;
            this.value = value;
            this.replyTo = replyTo;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public ActorRef<Response> getReplyTo() {
            return replyTo;
        }
    }

    /**
     * Command to clear conversation history
     */
    public static final class ClearHistory implements Command {
        private final ActorRef<Response> replyTo;

        public ClearHistory(ActorRef<Response> replyTo) {
            this.replyTo = replyTo;
        }

        public ActorRef<Response> getReplyTo() {
            return replyTo;
        }
    }

    /**
     * Response containing a message and highlighted courses
     */
    public static final class MessageResponse implements Response {
        private final String message;
        private final List<Course> highlightedCourses;

        public MessageResponse(String message, List<Course> highlightedCourses) {
            this.message = message;
            this.highlightedCourses = highlightedCourses;
        }

        public String getMessage() {
            return message;
        }

        public List<Course> getHighlightedCourses() {
            return highlightedCourses;
        }
    }

    /**
     * Response for preference actions
     */
    public static final class PreferenceResponse implements Response {
        private final boolean success;
        private final String message;

        public PreferenceResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Generic action response
     */
    public static final class ActionResponse implements Response {
        private final boolean success;
        private final String message;

        public ActionResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }
}