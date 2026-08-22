// Simple in-memory storage for demonstration
// In production, use PostgreSQL or similar

let eventId = 1;
let conversationId = 1;

const events = new Map(); // eventId -> event
const conversations = new Map(); // senderNumber -> conversation

export async function storeCallEvent(data) {
  const id = `event_${eventId++}`;
  const event = {
    id,
    callerNumber: data.callerNumber,
    status: data.status,
    type: data.type,
    deviceInitiated: data.deviceInitiated,
    timestamp: new Date(),
    messages: []
  };

  events.set(id, event);
  console.log(`💾 Call event stored: ${id}`);

  return event;
}

export async function storeWebhookEvent(data) {
  console.log(`💾 Webhook event stored: ${data.messageId}`);
  return data;
}

export async function getOrCreateConversation(senderNumber) {
  if (conversations.has(senderNumber)) {
    return conversations.get(senderNumber);
  }

  const id = `conv_${conversationId++}`;
  const conversation = {
    id,
    senderNumber,
    messages: [],
    createdAt: new Date(),
    lastStatus: 'Work'
  };

  conversations.set(senderNumber, conversation);
  console.log(`📌 New conversation created: ${id}`);

  return conversation;
}

export async function storeMessage(conversationId, messageData) {
  const conversation = Array.from(conversations.values()).find(
    c => c.id === conversationId
  );

  if (!conversation) {
    throw new Error(`Conversation not found: ${conversationId}`);
  }

  const message = {
    id: `msg_${Date.now()}`,
    ...messageData,
    timestamp: new Date()
  };

  conversation.messages.push(message);
  console.log(`📨 Message stored in conversation: ${message.id}`);

  return message;
}

export async function getConversationHistory(senderNumber) {
  const conversation = conversations.get(senderNumber);
  return conversation ? conversation.messages : [];
}
