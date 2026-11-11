package onix.core.event;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Event bus built for the render thread: posting is one map lookup and a plain
 * array loop, with no reflection and no allocation. A module that is switched
 * off has no listeners here, so it costs nothing.
 */
public final class EventBus {
	private static final Listener<?>[] NONE = new Listener<?>[0];
	private final Map<Class<?>, Listener<?>[]> listeners = new IdentityHashMap<Class<?>, Listener<?>[]>();

	public <E> void subscribe(Class<E> type, Listener<E> listener) {
		Listener<?>[] old = get(type);
		for (Listener<?> l : old) {
			if (l == listener) return;
		}
		Listener<?>[] next = new Listener<?>[old.length + 1];
		System.arraycopy(old, 0, next, 0, old.length);
		next[old.length] = listener;
		listeners.put(type, next);
	}

	public <E> void unsubscribe(Class<E> type, Listener<E> listener) {
		Listener<?>[] old = get(type);
		for (int i = 0; i < old.length; i++) {
			if (old[i] != listener) continue;
			Listener<?>[] next = new Listener<?>[old.length - 1];
			System.arraycopy(old, 0, next, 0, i);
			System.arraycopy(old, i + 1, next, i, old.length - i - 1);
			listeners.put(type, next);
			return;
		}
	}

	@SuppressWarnings("unchecked")
	public <E> void post(E event) {
		// The array is replaced, never edited, so a listener may unsubscribe mid-post.
		Listener<?>[] array = listeners.get(event.getClass());
		if (array == null) return;
		for (int i = 0; i < array.length; i++) {
			((Listener<E>) array[i]).on(event);
		}
	}

	public int count(Class<?> type) {
		return get(type).length;
	}

	private Listener<?>[] get(Class<?> type) {
		Listener<?>[] array = listeners.get(type);
		return array == null ? NONE : array;
	}
}
