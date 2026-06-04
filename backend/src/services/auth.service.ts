import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { v4 as uuidv4 } from 'uuid';
import { StorageService } from './storage.service';

const JWT_SECRET = process.env.JWT_SECRET || 'streamshop-dev-secret';
const JWT_EXPIRES_IN = '7d';

export interface User {
  id: string;
  username: string;
  account: string;
  avatarUrl: string;
  role: 'user' | 'admin';
  password: string;
  createdAt: string;
}

const userStorage = new StorageService<User>('users.json');

export class AuthService {
  static register(username: string, account: string, password: string): { user: Omit<User, 'password'>; token: string } | null {
    const existing = userStorage.query(u => u.account === account);
    if (existing.length > 0) return null;

    const hashed = bcrypt.hashSync(password, 10);
    const newId = uuidv4();
    const user: User = {
      id: newId,
      username,
      account,
      avatarUrl: `https://picsum.photos/seed/${username}/200/200`,
      role: 'user',
      password: hashed,
      createdAt: new Date().toISOString(),
    };
    userStorage.create(user);

    const token = jwt.sign({ id: user.id, username: user.username, role: user.role }, JWT_SECRET, { expiresIn: JWT_EXPIRES_IN });
    const { password: _, ...userWithoutPassword } = user;
    return { user: userWithoutPassword, token };
  }

  static login(account: string, password: string): { user: Omit<User, 'password'>; token: string } | null {
    const users = userStorage.query(u => u.account === account);
    if (users.length === 0) return null;

    const user = users[0];
    if (!bcrypt.compareSync(password, user.password)) return null;

    const token = jwt.sign({ id: user.id, username: user.username, role: user.role }, JWT_SECRET, { expiresIn: JWT_EXPIRES_IN });
    const { password: _, ...userWithoutPassword } = user;
    return { user: userWithoutPassword, token };
  }

  static verifyToken(token: string): { id: string; username: string; role: string } | null {
    try {
      return jwt.verify(token, JWT_SECRET) as { id: string; username: string; role: string };
    } catch {
      return null;
    }
  }

  static getUserById(id: string): Omit<User, 'password'> | undefined {
    const user = userStorage.findById(id);
    if (!user) return undefined;
    const { password: _, ...userWithoutPassword } = user;
    return userWithoutPassword;
  }

  static getAllUsers(): Omit<User, 'password'>[] {
    return userStorage.findAll().map(({ password: _, ...u }) => u);
  }
}
