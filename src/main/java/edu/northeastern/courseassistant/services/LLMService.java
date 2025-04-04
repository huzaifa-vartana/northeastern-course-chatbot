package edu.northeastern.courseassistant.services;

import edu.northeastern.courseassistant.model.Course;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatCompletionResult;

import java.util.*;

/**
 * LLM Service for generating natural language responses
 * about Northeastern University courses
 */
public class LLMService {
    private static final String MODEL = "gpt-4";
    private static final int MAX_TOKENS = 2000;
    private static final double TEMPERATURE = 0.7;

    private final OpenAiService openAiService;

    /**
     * Constructor - initializes the OpenAI service
     * @param openAiApiKey API key for OpenAI
     */
    public LLMService(String openAiApiKey) {
        this.openAiService = new OpenAiService(openAiApiKey);
    }

    /**
     * Generates a response based on the user query and relevant courses
     * @param userQuery The user's natural language query
     * @param relevantCourses List of courses retrieved by the RAG system
     * @param conversationHistory Previous conversation messages
     * @return Generated response
     */
    public String generateResponse(String userQuery, List<Course> relevantCourses,
                                   List<String> conversationHistory) {
        // Create the system prompt for the LLM
        String systemPrompt = createSystemPrompt();

        // Create the context with course information
        String contextPrompt = createContextPrompt(relevantCourses);

        // Create conversation history for context
        List<ChatMessage> messages = createConversationMessages(
                systemPrompt, contextPrompt, userQuery, conversationHistory);

        // Create the completion request
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(MODEL)
                .messages(messages)
                .maxTokens(MAX_TOKENS)
                .temperature(TEMPERATURE)
                .build();

        // Execute the request and return the response
        try {
            ChatCompletionResult result = openAiService.createChatCompletion(request);
            return result.getChoices().get(0).getMessage().getContent();
        } catch (Exception e) {
            e.printStackTrace();
            return "I'm sorry, I'm having trouble generating a response. Please try again.";
        }
    }

    /**
     * Creates the system prompt that guides the LLM's behavior
     */
    private String createSystemPrompt() {
        return "You are the Northeastern University Course Assistant, a helpful AI designed to help students " +
                "find and learn about courses at Northeastern University. Your goal is to provide accurate, " +
                "detailed information about courses in a conversational manner.\n\n" +
                "Guidelines:\n" +
                "1. Be concise but informative in your responses.\n" +
                "2. When multiple courses are relevant, summarize key details before going into specifics.\n" +
                "3. If a student asks about course availability, highlight enrollment numbers.\n" +
                "4. For schedule questions, format time and location information clearly.\n" +
                "5. If you don't have information about a specific course, acknowledge this and suggest " +
                "alternatives or recommend the student check the official course catalog.\n" +
                "6. Maintain a friendly, helpful tone appropriate for university students and faculty.\n" +
                "7. If the student uses a common course abbreviation (like 'OOD' for Object-Oriented Design), " +
                "recognize this and respond with relevant courses.\n\n" +
                "The information provided about courses is current as of the last data update. " +
                "All times are in Eastern Time (ET).";
    }

    /**
     * Creates a prompt with course information for context
     */
    private String createContextPrompt(List<Course> courses) {
        if (courses.isEmpty()) {
            return "No courses found matching the query.";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Here are the course details that may be relevant to the query:\n\n");

        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);
            builder.append("Course ").append(i + 1).append(":\n");
            builder.append("- Title: ").append(course.getTitle()).append("\n");
            builder.append("- Course ID: ").append(course.getSubject()).append(" ");
            builder.append(course.getNumber()).append("-").append(course.getSection()).append("\n");
            builder.append("- CRN: ").append(course.getCrn()).append("\n");
            builder.append("- Instructor: ").append(course.getInstructor()).append("\n");
            builder.append("- Schedule: ").append(course.getDays()).append(" ");
            builder.append(course.getTime()).append(" at ").append(course.getLocation()).append("\n");
            builder.append("- Campus: ").append(course.getCampus()).append("\n");
            builder.append("- Credits: ").append(course.getCredits()).append("\n");
            builder.append("- Enrollment: ").append(course.getEnrolled()).append("/");
            builder.append(course.getCapacity()).append(" (");
            builder.append(course.getAvailable()).append(" available)").append("\n\n");
        }

        return builder.toString();
    }

    /**
     * Creates the conversation messages including history
     */
    private List<ChatMessage> createConversationMessages(
            String systemPrompt, String contextPrompt, String userQuery, List<String> history) {

        List<ChatMessage> messages = new ArrayList<>();

        // Add system message
        messages.add(new ChatMessage("system", systemPrompt));

        // Add context message
        messages.add(new ChatMessage("system",
                "The following is the current course information available to you: \n\n" + contextPrompt));

        // Add conversation history (limited to last 10 exchanges to avoid token limits)
        int historyStartIndex = Math.max(0, history.size() - 10);
        for (int i = historyStartIndex; i < history.size(); i++) {
            String message = history.get(i);
            if (message.startsWith("User: ")) {
                messages.add(new ChatMessage("user", message.substring(6)));
            } else if (message.startsWith("Assistant: ")) {
                messages.add(new ChatMessage("assistant", message.substring(11)));
            }
        }

        // Add current user query
        messages.add(new ChatMessage("user", userQuery));

        return messages;
    }

    /**
     * Analyzes the response to extract course codes that should be highlighted
     * @param response The generated response
     * @param availableCourses Courses available for reference
     * @return List of courses that should be highlighted
     */
    public List<Course> extractHighlightedCourses(String response, List<Course> availableCourses) {
        List<Course> highlightedCourses = new ArrayList<>();
        Map<String, Course> courseByCRN = new HashMap<>();
        Map<String, Course> courseBySubjectNumber = new HashMap<>();

        // Create lookup maps for efficient course retrieval
        for (Course course : availableCourses) {
            courseByCRN.put(course.getCrn(), course);
            String courseKey = (course.getSubject() + course.getNumber()).toLowerCase();
            courseBySubjectNumber.put(courseKey, course);
        }

        // Look for CRN mentions (format: #12345)
        String[] words = response.split("\\s+");
        for (String word : words) {
            // Check for CRN format
            if (word.matches(".*#\\d{5}.*")) {
                String crn = word.replaceAll(".*#(\\d{5}).*", "$1");
                if (courseByCRN.containsKey(crn)) {
                    highlightedCourses.add(courseByCRN.get(crn));
                }
            }

            // Check for course code format (e.g., CSYE6200)
            if (word.matches("[A-Z]{2,4}\\d{4}.*")) {
                String cleanWord = word.replaceAll("[^A-Za-z0-9]", "");
                // Extract subject and number
                String subject = cleanWord.replaceAll("([A-Z]{2,4})\\d{4}.*", "$1");
                String number = cleanWord.replaceAll("[A-Z]{2,4}(\\d{4}).*", "$1");
                String key = (subject + number).toLowerCase();

                if (courseBySubjectNumber.containsKey(key)) {
                    highlightedCourses.add(courseBySubjectNumber.get(key));
                }
            }
        }

        // Reduce to unique courses
        return new ArrayList<>(new LinkedHashSet<>(highlightedCourses));
    }

    /**
     * Enhances the system prompt with specific user preferences or context
     * @param basePrompt The base system prompt
     * @param userPreferences User preferences to include
     * @return Enhanced system prompt
     */
    public String enhanceSystemPrompt(String basePrompt, Map<String, String> userPreferences) {
        StringBuilder enhancedPrompt = new StringBuilder(basePrompt);
        enhancedPrompt.append("\n\nAdditional Context:\n");

        if (userPreferences.containsKey("campus")) {
            enhancedPrompt.append("- The student is primarily interested in courses at the ");
            enhancedPrompt.append(userPreferences.get("campus")).append(" campus.\n");
        }

        if (userPreferences.containsKey("program")) {
            enhancedPrompt.append("- The student is enrolled in the ");
            enhancedPrompt.append(userPreferences.get("program")).append(" program.\n");
        }

        if (userPreferences.containsKey("interests")) {
            enhancedPrompt.append("- The student has expressed interest in courses related to: ");
            enhancedPrompt.append(userPreferences.get("interests")).append(".\n");
        }

        return enhancedPrompt.toString();
    }
}