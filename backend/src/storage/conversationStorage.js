import fs from "fs/promises";
import path from "path";

const dir = process.env.DATA_DIR || path.join(process.cwd(), "data");
const file = path.join(dir, "conversations.json");

async function load() {
  try { return JSON.parse(await fs.readFile(file, "utf8")); }
  catch { return {}; }
}
async function save(db) {
  await fs.mkdir(dir, {recursive:true});
  await fs.writeFile(file, JSON.stringify(db, null, 2));
}
export async function getConversation(phone) {
  const db=await load(); return db[phone] || null;
}
export async function storeConversation(conv) {
  const db=await load(); db[conv.callerNumber]=conv; await save(db); return conv;
}
