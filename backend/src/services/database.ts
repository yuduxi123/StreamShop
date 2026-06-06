import { MongoClient, Db } from 'mongodb';

let client: MongoClient | null = null;
let db: Db | null = null;
let connectionPromise: Promise<Db | null> | null = null;

export async function connectDB(): Promise<Db | null> {
  const uri = process.env.MONGODB_URI;
  if (!uri) return null;

  if (db) return db;

  if (!connectionPromise) {
    connectionPromise = (async () => {
      try {
        client = new MongoClient(uri);
        await client.connect();
        db = client.db('streamshop');
        console.log('[MongoDB] Connected');
        return db;
      } catch (e) {
        console.error('[MongoDB] Connection failed:', e);
        connectionPromise = null;
        return null;
      }
    })();
  }

  return connectionPromise;
}

export function getDB(): Db | null {
  return db;
}

export async function disconnectDB(): Promise<void> {
  if (client) {
    await client.close();
    client = null;
    db = null;
    connectionPromise = null;
  }
}
