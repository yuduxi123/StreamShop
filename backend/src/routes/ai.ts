import { Router, Response } from 'express';
import { authMiddleware, AuthRequest } from '../middleware/auth';
import { generateText, isConfigured, GenerateRequest } from '../services/ai.service';

const router = Router();

router.get('/status', (_req, res: Response) => {
  res.json({ configured: isConfigured() });
});

router.post('/generate', authMiddleware, async (req: AuthRequest, res: Response) => {
  try {
    const { type, context } = req.body as GenerateRequest;

    if (!type || !context) {
      res.status(400).json({ error: 'type and context are required' });
      return;
    }

    const validTypes = ['video_title', 'product_selling_points', 'product_description', 'live_commentary', 'product_recommendation'];
    if (!validTypes.includes(type)) {
      res.status(400).json({ error: `Invalid type. Must be one of: ${validTypes.join(', ')}` });
      return;
    }

    if (!isConfigured()) {
      res.status(503).json({ error: 'AI service is not configured. Set AI_API_KEY in .env' });
      return;
    }

    const result = await generateText({ type, context });
    res.json(result);
  } catch (err: any) {
    console.error('[AI] Generation error:', err.message);
    if (err.status === 401) {
      res.status(500).json({ error: 'AI API key is invalid. Please check AI_API_KEY in .env' });
      return;
    }
    if (err.status === 429) {
      res.status(429).json({ error: 'AI API rate limit reached. Please try again later.' });
      return;
    }
    res.status(500).json({ error: 'AI generation failed: ' + (err.message || 'unknown error') });
  }
});

export default router;
