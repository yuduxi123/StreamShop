import fs from 'fs';
import path from 'path';
import { getDB } from './database';
import { Document, Filter } from 'mongodb';

const DATA_DIR = path.join(__dirname, '..', 'data');

export class StorageService<T extends { id: string }> {
  private filePath: string;
  private collectionName: string;

  constructor(filename: string) {
    this.filePath = path.join(DATA_DIR, filename);
    this.collectionName = filename.replace(/\.json$/, '');
    this.ensureFile();
  }

  private ensureFile(): void {
    if (!fs.existsSync(DATA_DIR)) {
      fs.mkdirSync(DATA_DIR, { recursive: true });
    }
    if (!fs.existsSync(this.filePath)) {
      fs.writeFileSync(this.filePath, '[]', 'utf-8');
    }
  }

  private read(): T[] {
    try {
      const raw = fs.readFileSync(this.filePath, 'utf-8');
      return JSON.parse(raw) as T[];
    } catch {
      return [];
    }
  }

  write(data: T[]): void {
    fs.writeFileSync(this.filePath, JSON.stringify(data, null, 2), 'utf-8');
  }

  findAll(): T[] {
    return this.read();
  }

  findById(id: string): T | undefined {
    return this.read().find(item => item.id === id);
  }

  query(predicate: (item: T) => boolean): T[] {
    return this.read().filter(predicate);
  }

  create(item: T): T {
    const data = this.read();
    data.push(item);
    this.write(data);
    this.pushToMongo(item, 'insert');
    return item;
  }

  update(id: string, updates: Partial<T>): T | undefined {
    const data = this.read();
    const index = data.findIndex(item => item.id === id);
    if (index === -1) return undefined;
    data[index] = { ...data[index], ...updates };
    this.write(data);
    this.pushToMongo(data[index], 'update');
    return data[index];
  }

  delete(id: string): boolean {
    const data = this.read();
    const index = data.findIndex(item => item.id === id);
    if (index === -1) return false;
    const removed = data.splice(index, 1)[0];
    this.write(data);
    this.pushToMongo(removed, 'delete');
    return true;
  }

  paginate(page: number, limit: number): { data: T[]; total: number; page: number; limit: number } {
    const data = this.read();
    const total = data.length;
    const start = (page - 1) * limit;
    const paged = data.slice(start, start + limit);
    return { data: paged, total, page, limit };
  }

  // --- MongoDB sync ---

  private async pushToMongo(item: T, action: 'insert' | 'update' | 'delete'): Promise<void> {
    const db = getDB();
    if (!db) return;
    try {
      if (action === 'insert') {
        await db.collection(this.collectionName).insertOne(item as unknown as Document);
      } else if (action === 'update') {
        await db.collection(this.collectionName).updateOne(
          { id: item.id } as Filter<Document>,
          { $set: item as unknown as Document },
          { upsert: true },
        );
      } else {
        await db.collection(this.collectionName).deleteOne({ id: item.id } as Filter<Document>);
      }
    } catch { /* non-fatal */ }
  }

  async syncFromMongo(): Promise<boolean> {
    const db = getDB();
    if (!db) return false;
    try {
      const docs = await db.collection(this.collectionName).find({}).toArray();
      if (docs.length === 0) return false;
      const items = docs.map((d: any) => {
        const { _id, ...rest } = d;
        return rest as T;
      });
      this.write(items);
      return true;
    } catch {
      return false;
    }
  }
}
