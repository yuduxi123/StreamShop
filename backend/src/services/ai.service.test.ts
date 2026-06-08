import assert from 'assert';
import { generateFallbackText } from './ai.service';

const result = generateFallbackText({
  type: 'product_recommendation',
  context: {
    productName: '啄木鸟2026新款男款夹克',
    productDescription: '简约百搭时尚',
    productPrice: '127',
    productCategory: '男装',
  },
});

assert.equal(result.type, 'product_recommendation');
assert(result.text.includes('啄木鸟2026新款男款夹克'));
assert(result.text.includes('127'));
assert(result.text.length >= 30);

console.log('ai.service tests passed');
