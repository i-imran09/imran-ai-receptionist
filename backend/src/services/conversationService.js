const storage = require('../storage/storage');

const conversations = new Map();

async function initializeConversation(data) {
  const { callerNumber, currentStatus, callTimestamp } = data;

  let conversation = conversations.get(callerNumber);

  if (!conversation) {
    conversation = {
      id: `conv_${callerNumber}_${Date.now()}`,
      callerNumber,
      currentStatus,
      callTimestamp,
      messages: [],
      repeatCount: 1,
      previousCalls: [callTimestamp],
      initialTemplateStatus: 'pending',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };

    conversations.set(callerNumber, conversation);
    await storage.saveConversation(conversation);
  } else {
    // Increment repeat count
    conversation.repeatCount += 1;
    conversation.previousCalls.push(callTimestamp);
    conversation.updatedAt = new Date().toISOString();
    await storage.updateConversation(conversation);
  }

  return conversation;
}

async function getConversation(callerNumber) {
  let conversation = conversations.get(callerNumber);

  if (!conversation) {
    conversation = await storage.loadConversation(callerNumber);
    if (conversation) {
      conversations.set(callerNumber, conversation);
    }
  }

  return conversation;
}

async function updateConversation(conversationId, updates) {
  for (const [key, conversation] of conversations.entries()) {
    if (conversation.id === conversationId) {
      Object.assign(conversation, updates, {
        updatedAt: new Date().toISOString()
      });
      await storage.updateConversation(conversation);
      return conversation;
    }
  }

  throw new Error(`Conversation not found: ${conversationId}`);
}

async function addMessage(conversationId, messageData) {
  for (const [key, conversation] of conversations.entries()) {
    if (conversation.id === conversationId) {
      const message = {
        id: `msg_${Date.now()}`,
        ...messageData,
        timestamp: messageData.timestamp || new Date().toISOString()
      };

      conversation.messages.push(message);
      conversation.updatedAt = new Date().toISOString();
      await storage.updateConversation(conversation);
      return message;
    }
  }

  throw new Error(`Conversation not found: ${conversationId}`);
}

async function getAllConversations() {
  const allConversations = Array.from(conversations.values());
  return allConversations;
}

module.exports = {
  initializeConversation,
  getConversation,
  updateConversation,
  addMessage,
  getAllConversations
};
