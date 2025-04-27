package edu.northeastern.courseassistant;

import edu.northeastern.courseassistant.actors.ConversationManagerActor;
import edu.northeastern.courseassistant.protocol.ConversationProtocol;
import edu.northeastern.courseassistant.services.LLMService;
import edu.northeastern.courseassistant.services.RAGService;
import edu.northeastern.courseassistant.ui.WebServer;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Main application entry point for the Northeastern University Course Assistant
 */
public class NEUCourseAssistant {
    private static final Logger logger = LoggerFactory.getLogger(NEUCourseAssistant.class);

    public static void main(String[] args) {
        RAGService ragService = null;
        WebServer webServer = null;
        ActorSystem<Void> system = null;

        try {
            // Load configuration
            Properties config = loadConfig();
            String openAiApiKey = config.getProperty("openai.api.key");

            if (openAiApiKey == null || openAiApiKey.trim().isEmpty()) {
                logger.error("OpenAI API key is missing in the configuration");
                System.exit(1);
            }

            // Initialize services
            final RAGService finalRagService = new RAGService(openAiApiKey);
            ragService = finalRagService;
            LLMService llmService = new LLMService(openAiApiKey);

            // Start the actor system
            final ActorSystem<Void> finalSystem = createActorSystem(ragService, llmService);
            system = finalSystem;

            // Start the web server
            final WebServer finalWebServer = new WebServer(ragService, llmService);
            webServer = finalWebServer;
            webServer.start();

            logger.info("NEU Course Assistant started successfully");

            // Add shutdown hook to gracefully stop services
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down NEU Course Assistant...");

                // Close resources when application exits
                if (finalWebServer != null) {
                    try {
                        finalWebServer.stop();
                    } catch (Exception e) {
                        logger.error("Error stopping web server", e);
                    }
                }

                if (finalRagService != null) {
                    try {
                        finalRagService.close();
                    } catch (Exception e) {
                        logger.error("Error closing RAG service", e);
                    }
                }

                // Terminate the actor system
                if (finalSystem != null) {
                    finalSystem.terminate();
                }

                logger.info("NEU Course Assistant shutdown complete");
            }));

            // Block the main thread to keep the application running
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Failed to start NEU Course Assistant", e);
            System.exit(1);
        }
    }

    /**
     * Creates and starts the Akka actor system
     */
    private static ActorSystem<Void> createActorSystem(RAGService ragService, LLMService llmService) {
        Behavior<Void> rootBehavior = Behaviors.setup(context -> {
            // Create the ConversationManager actor
            ActorRef<ConversationProtocol.Command> conversationManager =
                    context.spawn(ConversationManagerActor.create(ragService, llmService), "conversation-manager");

            // Initialize and do any startup tasks
            // ...

            return Behaviors.empty();
        });

        return ActorSystem.create(rootBehavior, "neu-course-assistant");
    }

    /**
     * Loads configuration from application.properties
     */
    private static Properties loadConfig() {
        Properties properties = new Properties();
        try (InputStream input = NEUCourseAssistant.class.getClassLoader()
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

    /**
     * Application configuration properties
     */
    public static class Config {
        // Default configuration values
        private static final int DEFAULT_PORT = 8080;
        private static final int MAX_CONVERSATION_HISTORY = 20;
        private static final String DEFAULT_LLM_MODEL = "gpt-4";

        private final Properties properties;

        public Config(Properties properties) {
            this.properties = properties;
        }

        public String getOpenAiApiKey() {
            return properties.getProperty("openai.api.key");
        }

        public int getServerPort() {
            try {
                return Integer.parseInt(properties.getProperty("server.port", String.valueOf(DEFAULT_PORT)));
            } catch (NumberFormatException e) {
                return DEFAULT_PORT;
            }
        }

        public int getMaxConversationHistory() {
            try {
                return Integer.parseInt(properties.getProperty(
                        "conversation.max_history", String.valueOf(MAX_CONVERSATION_HISTORY)));
            } catch (NumberFormatException e) {
                return MAX_CONVERSATION_HISTORY;
            }
        }

        public String getLlmModel() {
            return properties.getProperty("llm.model", DEFAULT_LLM_MODEL);
        }

        public boolean isDebugMode() {
            return "true".equalsIgnoreCase(properties.getProperty("debug", "false"));
        }
    }
}