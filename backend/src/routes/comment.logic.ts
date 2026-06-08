export interface CommentUser {
  id: string;
  username?: string;
  avatarUrl?: string;
}

export interface CommentWithUser {
  userId: string;
  [key: string]: any;
}

export function enrichComment<T extends CommentWithUser>(comment: T, users: CommentUser[]): T & {
  user: { id: string; username?: string; avatarUrl?: string } | null;
} {
  const user = users.find(item => item.id === comment.userId);
  return {
    ...comment,
    user: user
      ? { id: user.id, username: user.username, avatarUrl: user.avatarUrl }
      : null,
  };
}
