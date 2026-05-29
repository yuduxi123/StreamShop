import { Router, Request, Response } from 'express';
import { AuthService } from '../services/auth.service';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { StorageService } from '../services/storage.service';

const userStorage = new StorageService<any>('users.json');

const router = Router();

router.post('/register', (req: Request, res: Response) => {
  const { username, password } = req.body;
  if (!username || !password) {
    res.status(400).json({ error: 'Username and password required' });
    return;
  }
  const result = AuthService.register(username, password);
  if (!result) {
    res.status(409).json({ error: 'Username already exists' });
    return;
  }
  res.status(201).json(result);
});

router.post('/login', (req: Request, res: Response) => {
  const { username, password } = req.body;
  if (!username || !password) {
    res.status(400).json({ error: 'Username and password required' });
    return;
  }
  const result = AuthService.login(username, password);
  if (!result) {
    res.status(401).json({ error: 'Invalid credentials' });
    return;
  }
  res.json(result);
});

router.get('/me', authMiddleware, (req: AuthRequest, res: Response) => {
  const user = AuthService.getUserById(req.user!.id);
  if (!user) {
    res.status(404).json({ error: 'User not found' });
    return;
  }
  res.json(user);
});

// PATCH /api/auth/me - update current user profile
router.patch('/me', authMiddleware, (req: AuthRequest, res: Response) => {
  const updates: any = {};
  if (req.body.avatarUrl !== undefined) updates.avatarUrl = req.body.avatarUrl;
  const updated = userStorage.update(req.user!.id, updates);
  if (!updated) {
    res.status(404).json({ error: 'User not found' });
    return;
  }
  const { password, ...user } = updated;
  res.json(user);
});

export default router;
