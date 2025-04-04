package edu.northeastern.courseassistant.protocol;


import edu.northeastern.courseassistant.model.Course;
import akka.actor.typed.ActorRef;

import java.util.List;
import java.util.Map;

/**
 * Protocol for RAG (Retrieval Augmented Generation) Actor
 */
public class RAGProtocol {
    private RAGProtocol() {} // Prevent instantiation

    /**
     * Base interface for all RAG commands
     */
    public interface Command {}

    /**
     * Command to search for courses based on a user query
     */
    public static final class SearchCourses implements Command {
        private final String query;
        private final String requestId;
        private final ActorRef<ConversationProtocol.Command> replyTo;

        public SearchCourses(String query, String requestId, ActorRef<ConversationProtocol.Command> replyTo) {
            this.query = query;
            this.requestId = requestId;
            this.replyTo = replyTo;
        }

        public String getQuery() { return query; }
        public String getRequestId() { return requestId; }
        public ActorRef<ConversationProtocol.Command> getReplyTo() { return replyTo; }
    }

    /**
     * Command to refresh course data
     */
    public static final class RefreshCourseData implements Command {
        private final ActorRef<RAGProtocol.Command> replyTo;

        public RefreshCourseData(ActorRef<RAGProtocol.Command> replyTo) {
            this.replyTo = replyTo;
        }

        public ActorRef<RAGProtocol.Command> getReplyTo() { return replyTo; }
    }

    /**
     * Response for course data refresh
     */
    public static final class CourseDataRefreshed implements Command {
        private final boolean success;
        private final String message;

        public CourseDataRefreshed(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}