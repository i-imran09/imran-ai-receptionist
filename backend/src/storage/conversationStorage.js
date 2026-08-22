import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const storageDir = path.join(__dirname, '../../storage/conversations');

// Ensure directory exists
if (!fs.existsSync(storageDir)) {
  fs.mkdirSync(storageDir, { recursive: true });
}

const getFilePath = (callerNumber) => {
  const normalized = callerNumber.replace(/[^0-9]/g, '');
  return path.join(storageDir, `${normalized}.json`);
};

export const storeConversation = async (conversation) => {
  return new Promise((resolve, reject) => {
    const filePath = getFilePath(conversation.callerNumber);
    const data = JSON.stringify(conversation, null, 2);
    
    fs.writeFile(filePath, data, 'utf8', (err) => {
      if (err) {
        console.error('[Storage] Write error:', err);
        reject(err);
      } else {
        console.log('[Storage] Conversation saved:', conversation.id);
        resolve();
      }
    });
  });
};

export const getConversation = async (callerNumber) => {
  return new Promise((resolve) => {
    const filePath = getFilePath(callerNumber);
    
    fs.readFile(filePath, 'utf8', (err, data) => {
      if (err) {
        if (err.code !== 'ENOENT') {
          console.error('[Storage] Read error:', err);
        }
        resolve(null);
      } else {
        try {
          resolve(JSON.parse(data));
        } catch (parseErr) {
          console.error('[Storage] Parse error:', parseErr);
          resolve(null);
        }
      }
    });
  });
};

export const addMessage = async (conversationId, message) => {
  // Implementation for adding message to conversation
  console.log('[Storage] Message added:', message.id);
};
