import { Router, Request, Response } from 'express';
import { AuthService } from '../services/auth.service';

const router = Router();

// GET /api/users/:id - public user profile
router.get('/:id', (req: Request, res: Response) => {
  const userId = req.params.id as string;
  const user = AuthService.getUserById(userId);
  if (!user) {
    res.status(404).json({ error: 'User not found' });
    return;
  }
  res.json({ ...user });
});

export default router;
