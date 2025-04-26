package edu.northeastern.courseassistant;

import edu.northeastern.courseassistant.services.LLMService;
import edu.northeastern.courseassistant.services.RAGService;
import edu.northeastern.courseassistant.ui.WebServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Main application entry point for the Northeastern University Course Assistant
 * Simplified version without Akka dependencies
 */
public class NEUCourseAssistantSimplified {
    private static final Logger logger = LoggerFactory.getLogger(NEUCourseAssistantSimplified.class);
    
    public static void main(String[] args) {
        try {
            // Load configuration
            Properties properties = loadConfig();
            
            // Get the OpenAI API key from the configuration
            String openAiApiKey = properties.getProperty("openai.api.key");
            
            if (openAiApiKey == null || openAiApiKey.trim().isEmpty() 
                    || openAiApiKey.equals("your_openai_api_key_here")) {
                System.out.println("WARNING: OpenAI API key is not set or is using the default value.");
                System.out.println("The system will use mock data instead of actual API calls.");
                System.out.println("Set a valid API key in application.properties or as an environment variable.");
            }
            
            // Initialize services
            RAGService ragService = new RAGService(openAiApiKey);
            LLMService llmService = new LLMService(openAiApiKey);
            
            // Start the web server
            WebServer webServer = new WebServer(ragService, llmService);
            webServer.start();
            
            logger.info("NEU Course Assistant started successfully");
            
        } catch (Exception e) {
            logger.error("Failed to start NEU Course Assistant", e);
            System.exit(1);
        }
    }
    
    /**
     * Loads configuration from application.properties
     */
    private static Properties loadConfig() {
        Properties properties = new Properties();
        try (InputStream input = NEUCourseAssistantSimplified.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            
            if (input == null) {
                logger.warn("application.properties not found, using environment variables");
                // Try to get from environment variables
                String apiKey = System.getenv("OPENAI_API_KEY");
                if (apiKey != null) {
                    properties.setProperty("openai.api.key", apiKey);
                }
            } else {
                properties.load(input);
            }
            
        } catch (Exception e) {
            logger.error("Error loading configuration", e);
        }
        return properties;
    }
}