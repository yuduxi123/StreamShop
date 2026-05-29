import fs from 'fs';
import path from 'path';

const DATA_DIR = path.join(__dirname, '..', 'data');

export class StorageService<T extends { id: string }> {
  private filePath: string;

  constructor(filename: string) {
    this.filePath = path.join(DATA_DIR, filename);
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

  private write(data: T[]): void {
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
    return item;
  }

  update(id: string, updates: Partial<T>): T | undefined {
    const data = this.read();
    const index = data.findIndex(item => item.id === id);
    if (index === -1) return undefined;
    data[index] = { ...data[index], ...updates };
    this.write(data);
    return data[index];
  }

  delete(id: string): boolean {
    const data = this.read();
    const index = data.findIndex(item => item.id === id);
    if (index === -1) return false;
    data.splice(index, 1);
    this.write(data);
    return true;
  }

  paginate(page: number, limit: number): { data: T[]; total: number; page: number; limit: number } {
    const data = this.read();
    const total = data.length;
    const start = (page - 1) * limit;
    const paged = data.slice(start, start + limit);
    return { data: paged, total, page, limit };
  }
}
