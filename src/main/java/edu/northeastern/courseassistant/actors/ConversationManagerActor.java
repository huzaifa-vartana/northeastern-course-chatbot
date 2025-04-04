package edu.northeastern.courseassistant.actors;

import edu.northeastern.courseassistant.model.Course;
import edu.northeastern.courseassistant.protocol.ConversationProtocol;
import edu.northeastern.courseassistant.protocol.LLMProtocol;
import edu.northeastern.courseassistant.protocol.RAGProtocol;
import edu.northeastern.courseassistant.services.RAGService;
import edu.northeastern.courseassistant.services.LLMService;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Conversation Manager Actor
 *
 * This actor is responsible for:
 * 1. Managing the conversation flow between the user and the system
 * 2. Coordinating between the RAG and LLM components
 * 3. Maintaining conversation context and history
 * 4. Handling user preferences and session state
 */
public class ConversationManagerActor extends AbstractBehavior<ConversationProtocol.Command> {

    private final ActorRef<RAGProtocol.Command> ragActor;
    private final ActorRef<LLMProtocol.Command> llmActor;
    private final RAGService ragService;
    private final LLMService llmService;

    // Conversation state
    private final List<String> conversationHistory = new ArrayList<>();
    private final Map<String, String> userPreferences = new HashMap<>();

    // Session tracking
    private final Map<String, ActorRef<ConversationProtocol.Response>> pendingRequests = new HashMap<>();

    private ConversationManagerActor(ActorContext<ConversationProtocol.Command> context,
                                     RAGService ragService, LLMService llmService) {
        super(context);
        this.ragService = ragService;
        this.llmService = llmService;

        // Create child actors
        this.ragActor = context.spawn(RAGActor.create(ragService), "rag-actor");
        this.llmActor = context.spawn(LLMActor.create(llmService), "llm-actor");
    }

    public static Behavior<ConversationProtocol.Command> create(RAGService ragService, LLMService llmService) {
        return Behaviors.setup(context -> new ConversationManagerActor(context, ragService, llmService));
    }

    @Override
    public Receive<ConversationProtocol.Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(ConversationProtocol.ProcessUserMessage.class, this::onProcessUserMessage)
                .onMessage(ConversationProtocol.RAGResultsReceived.class, this::onRAGResultsReceived)
                .onMessage(ConversationProtocol.LLMResponseReceived.class, this::onLLMResponseReceived)
                .onMessage(ConversationProtocol.SetPreference.class, this::onSetPreference)
                .onMessage(ConversationProtocol.ClearHistory.class, this::onClearHistory)
                .build();
    }

    /**
     * Handles a new user message
     */
    private Behavior<ConversationProtocol.Command> onProcessUserMessage(
            ConversationProtocol.ProcessUserMessage message) {

        getContext().getLog().info("Processing user message: {}", message.getUserMessage());

        // Store the request for later completion
        pendingRequests.put(message.getRequestId(), message.getReplyTo());

        // Add message to conversation history
        conversationHistory.add("User: " + message.getUserMessage());

        // Forward the query to RAG actor
        ragActor.tell(new RAGProtocol.SearchCourses(
                message.getUserMessage(),
                message.getRequestId(),
                getContext().getSelf()));

        return this;
    }

    /**
     * Handles RAG search results
     */
    private Behavior<ConversationProtocol.Command> onRAGResultsReceived(
            ConversationProtocol.RAGResultsReceived message) {

        getContext().getLog().info("Received RAG results for request {}: {} courses found",
                message.getRequestId(), message.getRelevantCourses().size());

        // Forward to LLM for response generation
        llmActor.tell(new LLMProtocol.GenerateResponse(
                message.getUserMessage(),
                message.getRelevantCourses(),
                new ArrayList<>(conversationHistory),  // Make a copy to avoid concurrent modification
                userPreferences,
                message.getRequestId(),
                getContext().getSelf()));

        return this;
    }

    /**
     * Handles LLM generated response
     */
    private Behavior<ConversationProtocol.Command> onLLMResponseReceived(
            ConversationProtocol.LLMResponseReceived message) {

        getContext().getLog().info("Received LLM response for request {}", message.getRequestId());

        // Add response to conversation history
        conversationHistory.add("Assistant: " + message.getResponse());

        // Limit conversation history size
        if (conversationHistory.size() > 20) {
            int removeCount = conversationHistory.size() - 20;
            conversationHistory.subList(0, removeCount).clear();
        }

        // Find the pending request and complete it
        ActorRef<ConversationProtocol.Response> replyTo = pendingRequests.remove(message.getRequestId());
        if (replyTo != null) {
            replyTo.tell(new ConversationProtocol.MessageResponse(
                    message.getResponse(),
                    message.getHighlightedCourses()));
        } else {
            getContext().getLog().warn("No pending request found for ID: {}", message.getRequestId());
        }

        return this;
    }

    /**
     * Handles setting a user preference
     */
    private Behavior<ConversationProtocol.Command> onSetPreference(ConversationProtocol.SetPreference message) {
        userPreferences.put(message.getKey(), message.getValue());
        message.getReplyTo().tell(new ConversationProtocol.PreferenceResponse(true,
                "Preference set: " + message.getKey() + " = " + message.getValue()));
        return this;
    }

    /**
     * Handles clearing conversation history
     */
    private Behavior<ConversationProtocol.Command> onClearHistory(ConversationProtocol.ClearHistory message) {
        conversationHistory.clear();
        message.getReplyTo().tell(new ConversationProtocol.ActionResponse(true,
                "Conversation history cleared."));
        return this;
    }
}