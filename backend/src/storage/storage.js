const storageType = process.env.STORAGE_TYPE || 'json';
let storageAdapter;

if (storageType === 'json') {
  storageAdapter = require('./jsonStorage');
} else {
  // Fallback to JSON for unknown types
  storageAdapter = require('./jsonStorage');
}

module.exports = storageAdapter;
