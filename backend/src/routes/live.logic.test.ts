import assert from 'assert';
import { buildLiveStreamUrl, resolveLiveStartUpdates } from './live.logic';

assert.equal(
  buildLiveStreamUrl('10.208.69.9', 'room-123'),
  'http://10.208.69.9:8000/live/room-123.flv'
);

assert.deepEqual(
  resolveLiveStartUpdates('10.208.69.9', {
    id: 'room-123',
    status: 'ended',
    onlineCount: 9,
    likeCount: 7,
  }),
  {
    status: 'live',
    streamUrl: 'http://10.208.69.9:8000/live/room-123.flv',
    onlineCount: 0,
    likeCount: 0,
  }
);

assert.deepEqual(
  resolveLiveStartUpdates('10.208.69.9', {
    id: 'room-123',
    status: 'ended',
    streamUrl: 'http://172.25.160.1:8000/live/room-123.flv',
    onlineCount: 3,
    likeCount: 4,
  }),
  {
    status: 'live',
    streamUrl: 'http://10.208.69.9:8000/live/room-123.flv',
    onlineCount: 0,
    likeCount: 0,
  }
);

console.log('live.logic tests passed');
