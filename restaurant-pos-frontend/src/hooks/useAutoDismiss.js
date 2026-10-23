import { useEffect, useRef } from 'react';

/**
 * Custom hook to automatically dismiss notifications after a duration (default 3 seconds / 3000ms).
 *
 * Requirements satisfied:
 * - Automatically dismisses after 3 seconds (3000ms).
 * - Resets the timer whenever a new notification message appears, avoiding stale premature dismissals.
 * - Cleans up timeout timers on component unmount or when message changes, preventing memory leaks.
 * - Does not touch or dismiss error messages or warnings.
 *
 * @param {string|null} message - The current notification message.
 * @param {Function} onDismiss - Callback to clear the notification message.
 * @param {number} [durationMs=3000] - Duration in milliseconds before dismissing (default 3000).
 */
export const useAutoDismiss = (message, onDismiss, durationMs = 3000) => {
  const onDismissRef = useRef(onDismiss);

  useEffect(() => {
    onDismissRef.current = onDismiss;
  });

  useEffect(() => {
    if (!message) return;

    const timer = setTimeout(() => {
      if (onDismissRef.current) {
        onDismissRef.current();
      }
    }, durationMs);

    return () => {
      clearTimeout(timer);
    };
  }, [message, durationMs]);
};

export default useAutoDismiss;
