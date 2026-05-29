import { Router, Request, Response } from 'express';
import { StorageService } from '../services/storage.service';

interface ProductData {
  id: string;
  title: string;
  description: string;
  price: number;
  originalPrice: number;
  coverUrl: string;
  stock: number;
  salesCount: number;
  status: string;
  category: string;
  createdAt: string;
}

const productStorage = new StorageService<ProductData>('products.json');
const router = Router();

// GET /api/recommendations?productId=&limit=10
router.get('/', (req: Request, res: Response) => {
  const productId = req.query.productId as string;
  const limit = parseInt(req.query.limit as string) || 10;
  const all = productStorage.findAll().filter(p => p.status === 'active');

  if (!productId) {
    // No reference product: return top selling
    const sorted = all.sort((a, b) => b.salesCount - a.salesCount);
    return res.json(sorted.slice(0, limit));
  }

  const source = all.find(p => p.id === productId);
  if (!source) {
    return res.json(all.sort((a, b) => b.salesCount - a.salesCount).slice(0, limit));
  }

  // Score products by category match + price similarity + popularity
  const scored = all
    .filter(p => p.id !== productId)
    .map(p => {
      let score = 0;
      if (p.category === source.category) score += 50;
      // Price similarity (closer = better)
      const priceDiff = Math.abs(p.price - source.price);
      if (priceDiff < 50) score += 30;
      else if (priceDiff < 100) score += 15;
      // Popularity bonus
      score += Math.min(20, p.salesCount / 100);
      return { ...p, _score: score };
    })
    .sort((a, b) => b._score - a._score);

  const result = scored.slice(0, limit).map(({ _score, ...p }) => p);
  res.json(result);
});

export default router;
