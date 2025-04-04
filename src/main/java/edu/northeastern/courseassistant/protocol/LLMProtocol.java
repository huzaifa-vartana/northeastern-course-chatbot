package edu.northeastern.courseassistant.protocol;



import edu.northeastern.courseassistant.model.Course;
import akka.actor.typed.ActorRef;

import java.util.List;
import java.util.Map;


/**
 * Protocol for LLM (Large Language Model) Actor
 */
public class LLMProtocol {
    private LLMProtocol() {} // Prevent instantiation

    /**
     * Base interface for all LLM commands
     */
    public interface Command {}

    /**
     * Command to generate a response based on user query and retrieved courses
     */
    public static final class GenerateResponse implements Command {
        private final String userQuery;
        private final List<Course> contextCourses;
        private final List<String> conversationHistory;
        private final Map<String, String> userPreferences;
        private final String requestId;
        private final ActorRef<ConversationProtocol.Command> replyTo;

        public GenerateResponse(String userQuery, List<Course> contextCourses,
                                List<String> conversationHistory, Map<String, String> userPreferences,
                                String requestId, ActorRef<ConversationProtocol.Command> replyTo) {
            this.userQuery = userQuery;
            this.contextCourses = contextCourses;
            this.conversationHistory = conversationHistory;
            this.userPreferences = userPreferences;
            this.requestId = requestId;
            this.replyTo = replyTo;
        }

        public String getUserQuery() { return userQuery; }
        public List<Course> getContextCourses() { return contextCourses; }
        public List<String> getConversationHistory() { return conversationHistory; }
        public Map<String, String> getUserPreferences() { return userPreferences; }
        public String getRequestId() { return requestId; }
        public ActorRef<ConversationProtocol.Command> getReplyTo() { return replyTo; }
    }
}

