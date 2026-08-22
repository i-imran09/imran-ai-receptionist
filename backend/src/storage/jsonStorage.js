const fs = require('fs');
const path = require('path');

const STORAGE_PATH = process.env.STORAGE_PATH || './storage';
const CONVERSATIONS_DIR = path.join(STORAGE_PATH, 'conversations');

// Ensure storage directory exists
if (!fs.existsSync(STORAGE_PATH)) {
  fs.mkdirSync(STORAGE_PATH, { recursive: true });
}

if (!fs.existsSync(CONVERSATIONS_DIR)) {
  fs.mkdirSync(CONVERSATIONS_DIR, { recursive: true });
}

function getConversationFilePath(callerNumber) {
  const normalized = callerNumber.replace(/[^0-9]/g, '');
  return path.join(CONVERSATIONS_DIR, `${normalized}.json`);
}

async function saveConversation(conversation) {
  return new Promise((resolve, reject) => {
    const filePath = getConversationFilePath(conversation.callerNumber);
    const data = JSON.stringify(conversation, null, 2);

    fs.writeFile(filePath, data, 'utf8', (err) => {
      if (err) {
        console.error(`[Storage] Error saving conversation:`, err);
        reject(err);
      } else {
        console.log(`[${new Date().toISOString()}] Conversation saved:`, conversation.id);
        resolve();
      }
    });
  });
}

async function loadConversation(callerNumber) {
  return new Promise((resolve) => {
    const filePath = getConversationFilePath(callerNumber);

    fs.readFile(filePath, 'utf8', (err, data) => {
      if (err) {
        if (err.code !== 'ENOENT') {
          console.error(`[Storage] Error loading conversation:`, err);
        }
        resolve(null);
      } else {
        try {
          const conversation = JSON.parse(data);
          resolve(conversation);
        } catch (parseErr) {
          console.error(`[Storage] Error parsing conversation:`, parseErr);
          resolve(null);
        }
      }
    });
  });
}

async function updateConversation(conversation) {
  return saveConversation(conversation);
}

async function loadAllConversations() {
  return new Promise((resolve) => {
    fs.readdir(CONVERSATIONS_DIR, 'utf8', async (err, files) => {
      if (err) {
        console.error(`[Storage] Error reading conversations dir:`, err);
        resolve([]);
        return;
      }

      const conversations = [];
      for (const file of files) {
        if (file.endsWith('.json')) {
          const filePath = path.join(CONVERSATIONS_DIR, file);
          try {
            const data = fs.readFileSync(filePath, 'utf8');
            const conversation = JSON.parse(data);
            conversations.push(conversation);
          } catch (err) {
            console.error(`[Storage] Error loading ${file}:`, err);
          }
        }
      }

      resolve(conversations);
    });
  });
}

module.exports = {
  saveConversation,
  loadConversation,
  updateConversation,
  loadAllConversations
};
