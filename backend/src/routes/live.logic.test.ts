import assert from 'assert';
import {
  buildLiveStreamUrl,
  getNextLiveRoomProductDisplayOrder,
  resolveLiveStartUpdates,
  sortLiveRoomProductBindings,
} from './live.logic';

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

assert.deepEqual(
  sortLiveRoomProductBindings([
    { id: 'room_product-3', liveRoomId: 'room', productId: 'product-3', displayOrder: 2, isExplaining: false },
    { id: 'room_product-1', liveRoomId: 'room', productId: 'product-1', displayOrder: 0, isExplaining: false },
    { id: 'room_product-2', liveRoomId: 'room', productId: 'product-2', displayOrder: 1, isExplaining: true },
  ]).map(binding => binding.productId),
  ['product-1', 'product-2', 'product-3']
);

assert.equal(
  getNextLiveRoomProductDisplayOrder([
    { id: 'room_product-1', liveRoomId: 'room', productId: 'product-1', displayOrder: 0, isExplaining: false },
    { id: 'room_product-3', liveRoomId: 'room', productId: 'product-3', displayOrder: 2, isExplaining: false },
  ]),
  3
);

assert.equal(getNextLiveRoomProductDisplayOrder([]), 0);

console.log('live.logic tests passed');
