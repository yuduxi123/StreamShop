import assert from 'assert';
import { enrichComment } from './comment.logic';

const comment = {
  id: 'comment-1',
  userId: 'user-1',
  targetType: 'video',
  targetId: 'video-1',
  content: '好吃',
  likeCount: 0,
  rating: 0,
  createdAt: '2026-06-08T12:00:00.000Z',
};

const users = [
  { id: 'user-1', username: '杨君熠', avatarUrl: 'http://localhost:3000/uploads/avatar.jpg' },
  { id: 'user-2', username: '其他用户', avatarUrl: '' },
];

assert.deepEqual(enrichComment(comment, users), {
  ...comment,
  user: {
    id: 'user-1',
    username: '杨君熠',
    avatarUrl: 'http://localhost:3000/uploads/avatar.jpg',
  },
});

assert.deepEqual(enrichComment({ ...comment, userId: 'missing-user' }, users), {
  ...comment,
  userId: 'missing-user',
  user: null,
});

console.log('comment.logic tests passed');
