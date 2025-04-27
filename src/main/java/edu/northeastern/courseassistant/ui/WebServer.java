package edu.northeastern.courseassistant.ui;

import edu.northeastern.courseassistant.model.Course;
import edu.northeastern.courseassistant.services.LLMService;
import edu.northeastern.courseassistant.services.RAGService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web server for the Course Assistant UI
 */
public class WebServer {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);
    private static final int PORT = 8080;

    private final RAGService ragService;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    private Server server;

    /**
     * Constructor
     * @param ragService RAG service for course retrieval
     * @param llmService LLM service for response generation
     */
    public WebServer(RAGService ragService, LLMService llmService) {
        this.ragService = ragService;
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Starts the web server
     */
    public void start() throws Exception {
        server = new Server();

        // HTTP Configuration
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);
        httpConfig.setSendDateHeader(false);

        // Create HTTP connector
        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        connector.setPort(PORT);

        // Configure connection idle timeout (60 seconds instead of default 30)
        connector.setIdleTimeout(60000);

        // Add connector to server
        server.addConnector(connector);

        // Resource handler for static files
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(false);
        resourceHandler.setWelcomeFiles(new String[]{"index.html"});
        resourceHandler.setResourceBase("src/main/resources/static");

        // Servlet context handler for the API
        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.setContextPath("/api");

        // Register chat API servlet
        contextHandler.addServlet(new ServletHolder(new ChatServlet()), "/chat");

        // Combine handlers
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{contextHandler, resourceHandler});
        server.setHandler(handlers);

        // Start the server
        server.start();
        logger.info("Server started on port " + PORT);

        // Don't join the server thread, so we can return control to the caller
        // server.join();
    }

    /**
     * Stops the server
     */
    public void stop() throws Exception {
        if (server != null && server.isRunning()) {
            server.stop();
            logger.info("Server stopped");
        }
    }

    /**
     * Chat API servlet that handles user messages
     */
    private class ChatServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            try {
                // Parse JSON request
                Map<String, Object> requestData = objectMapper.readValue(
                        req.getInputStream(),
                        new TypeReference<Map<String, Object>>() {});

                String userMessage = (String) requestData.get("message");
                logger.info("Received message: " + userMessage);

                // Get or create session
                HttpSession session = req.getSession(true);

                // Get conversation history from session
                @SuppressWarnings("unchecked")
                List<String> conversationHistory = (List<String>) session.getAttribute("conversationHistory");
                if (conversationHistory == null) {
                    conversationHistory = new ArrayList<>();
                    session.setAttribute("conversationHistory", conversationHistory);
                }

                // Add user message to conversation history
                conversationHistory.add("User: " + userMessage);

                // Find relevant courses using RAG
                List<Course> relevantCourses = ragService.enhancedSearch(userMessage);

                // Generate response using LLM
                String response = llmService.generateResponse(userMessage, relevantCourses, conversationHistory);

                // Extract highlighted courses
                List<Course> highlightedCourses = llmService.extractHighlightedCourses(response, relevantCourses);

                // Format response with course cards if needed
                String formattedResponse = formatResponseWithCourseCards(response, highlightedCourses);

                // Add assistant response to conversation history
                conversationHistory.add("Assistant: " + response);

                // Limit conversation history size (keep last 20 messages)
                if (conversationHistory.size() > 20) {
                    conversationHistory = conversationHistory.subList(
                            conversationHistory.size() - 20,
                            conversationHistory.size());
                    session.setAttribute("conversationHistory", conversationHistory);
                }

                // Prepare response
                ObjectNode jsonResponse = objectMapper.createObjectNode();
                jsonResponse.put("response", formattedResponse);

                // Send response
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                resp.getWriter().write(objectMapper.writeValueAsString(jsonResponse));
            } catch (Exception e) {
                logger.error("Error processing request", e);

                // Send error response
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");

                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "An error occurred while processing your request");
                resp.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            }
        }

        /**
         * Formats the response with course cards for highlighted courses
         */
        private String formatResponseWithCourseCards(String response, List<Course> highlightedCourses) {
            if (highlightedCourses.isEmpty()) {
                return response;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("<p>").append(response).append("</p>");

            // Add course cards
            for (Course course : highlightedCourses) {
                sb.append(course.toHtml());
            }

            return sb.toString();
        }
    }

    /**
     * Type reference for Jackson JSON deserialization
     */
    private static class TypeReference<T> extends com.fasterxml.jackson.core.type.TypeReference<T> {
    }
}