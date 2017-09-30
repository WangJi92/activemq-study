#SimpleAsyncTaskExecutor
异步执行用户任务的SimpleAsyncTaskExecutor。每次执行客户提交给它的任务时，它会启动新的线程，并允许开发者控制并发线程的上限（concurrencyLimit），从而起到一定的资源节流作用。默认时，concurrencyLimit取值为-1，即不启用资源节流。
```xml
<bean id="simpleAsyncTaskExecutor"   
    class="org.springframework.core.task.SimpleAsyncTaskExecutor">  
    <property name="daemon" value="true"/>  
    <property name="concurrencyLimit" value="2"/>  
    <property name="threadNamePrefix" value="simpleAsyncTaskExecutor"/>
</bean>  
```
```java
public class SimpleAsyncTaskExecutor extends CustomizableThreadCreator implements AsyncListenableTaskExecutor, Serializable {

    private final SimpleAsyncTaskExecutor.ConcurrencyThrottleAdapter concurrencyThrottle = new SimpleAsyncTaskExecutor.ConcurrencyThrottleAdapter();
    private ThreadFactory threadFactory;
    //设置最大的线程数量
    public void setConcurrencyLimit(int concurrencyLimit) {
            this.concurrencyThrottle.setConcurrencyLimit(concurrencyLimit);
    }
    //是否开启了限流 限流数量大于0？
    public final boolean isThrottleActive() {
            return this.concurrencyThrottle.isThrottleActive();
    }
    //1.是否开启限流 否则不开启限流处理
    //2.执行开始之前检测是否可以满足要求 当前数量++
    //3.开启限流将执行的Runable进行封装，执行完成调用final方法 当前数量--
    public void execute(Runnable task, long startTimeout) {
            Assert.notNull(task, "Runnable must not be null");
            if(this.isThrottleActive() && startTimeout > 0L) {
                this.concurrencyThrottle.beforeAccess();
                this.doExecute(new SimpleAsyncTaskExecutor.ConcurrencyThrottlingRunnable(task));
            } else {
                this.doExecute(task);
            }
      }
     //异步提交有返回值
    public Future<?> submit(Runnable task) {
          FutureTask future = new FutureTask(task, (Object)null);
          this.execute(future, 9223372036854775807L);
          return future;
      }
  
      public <T> Future<T> submit(Callable<T> task) {
          FutureTask future = new FutureTask(task);
          this.execute(future, 9223372036854775807L);
          return future;
      }
  
      public ListenableFuture<?> submitListenable(Runnable task) {
          ListenableFutureTask future = new ListenableFutureTask(task, (Object)null);
          this.execute(future, 9223372036854775807L);
          return future;
      }
  
      public <T> ListenableFuture<T> submitListenable(Callable<T> task) {
          ListenableFutureTask future = new ListenableFutureTask(task);
          this.execute(future, 9223372036854775807L);
          return future;
      }
      //拥有工厂？没有的话调用父类可以设置各种参数的创建线程
      protected void doExecute(Runnable task) {
          Thread thread = this.threadFactory != null?this.threadFactory.newThread(task):this.createThread(task);
          thread.start();
      }
      //父类的方法，方便配置线程
      public Thread createThread(Runnable runnable) {
      		Thread thread = new Thread(getThreadGroup(), runnable, nextThreadName());
      		thread.setPriority(getThreadPriority());
      		thread.setDaemon(isDaemon());
      		return thread;
      	}

    
 }
```

>   1. 支持限流处理 2.异步注册线程返回结果

###限流处理其实就是在执行任务之前和之后对于当前线程数量进行统计
内部类的实现
```java
//下面只是对于操作进行简单的封装，最真的实现还是抽象的ConcurrencyThrottleSupport
  private static class ConcurrencyThrottleAdapter extends ConcurrencyThrottleSupport {
        private ConcurrencyThrottleAdapter() {
        }

        protected void beforeAccess() {
            super.beforeAccess();
        }

        protected void afterAccess() {
            super.afterAccess();
        }
    }
```
```java
//这里是对于Runable对象执行在次封装，在执行完毕后处理限流操作
private class ConcurrencyThrottlingRunnable implements Runnable {
        private final Runnable target;

        public ConcurrencyThrottlingRunnable(Runnable target) {
            this.target = target;
        }

        public void run() {
            try {
                this.target.run();
            } finally {
                SimpleAsyncTaskExecutor.this.concurrencyThrottle.afterAccess();
            }

        }
    }
```
简单的通过synchronized和wati and notify达到控制线程数量的效果，从而实现限流的策略。
```java
public abstract class ConcurrencyThrottleSupport implements Serializable {
    protected transient Log logger = LogFactory.getLog(this.getClass());
    private transient Object monitor = new Object();
    private int concurrencyLimit = -1;
    private int concurrencyCount = 0;
    public void setConcurrencyLimit(int concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
    }

    public int getConcurrencyLimit() {
        return this.concurrencyLimit;
    }

    public boolean isThrottleActive() {
        return this.concurrencyLimit > 0;
    }
    protected void beforeAccess() {
        if(this.concurrencyLimit == 0) {
            throw new IllegalStateException("没有设置限制");
        } else {
            if(this.concurrencyLimit > 0) {
                boolean debug = this.logger.isDebugEnabled();
                Object var2 = this.monitor;
                synchronized(this.monitor) {
                    boolean interrupted = false;
                    while(this.concurrencyCount >= this.concurrencyLimit) {
                        if(interrupted) {
                            throw new IllegalStateException("Thread was interrupted while waiting for invocation access, but concurrency limit still does not allow for entering");
                        }
                        try {
                            this.monitor.wait();
                        } catch (InterruptedException var6) {
                            Thread.currentThread().interrupt();
                            interrupted = true;
                        }
                    }
                    ++this.concurrencyCount;
                }
            }

        }
    }
    protected void afterAccess() {
        if(this.concurrencyLimit >= 0) {
            Object var1 = this.monitor;
            synchronized(this.monitor) {
                --this.concurrencyCount;
                this.monitor.notify();
            }
        }

    }
}
```
###异步监听获取线程的结果，其实这个不算这里面的实现
ListenableFutureTask 其实主要是依靠FutureTask这个JDK的封装,覆盖了原始的run方法，在run中封装可以获取到线程的返回值。
ListenableFutureTask 在次封装，由于FutureTask执行完成之后会调用done（）空方法，ListenableFutureTask覆盖done方法可以获取到执行的结果，然后在调用前期注册的错误处理或者成功处理的方法，即可到达异步处理的效果。
类似于回调的效果
```java
public interface SuccessCallback<T> {

	/**
	 * Called when the {@link ListenableFuture} successfully completes.
	 * @param result the result
	 */
	void onSuccess(T result);
}
public interface FailureCallback {

	/**
	 * Called when the {@link ListenableFuture} fails to complete.
	 * @param ex the exception that triggered the failure
	 */
	void onFailure(Throwable ex);
}

public interface ListenableFuture<T> extends Future<T> {
    //成功和失败的集合
    void addCallback(ListenableFutureCallback<? super T> var1);

    void addCallback(SuccessCallback<? super T> var1, FailureCallback var2);
}
```
实现类(ListenableFutureTask)可有返回值，可被监听的，注册监听，这里可以注册监听者放在一个单独的类中去处理，很好的分配工作ListenableFutureCallbackRegistry
```java
public class ListenableFutureTask<T> extends FutureTask<T> implements ListenableFuture<T> {
    private final ListenableFutureCallbackRegistry<T> callbacks = new ListenableFutureCallbackRegistry();

    public ListenableFutureTask(Callable<T> callable) {
        super(callable);
    }

    public ListenableFutureTask(Runnable runnable, T result) {
        super(runnable, result);
    }

    public void addCallback(ListenableFutureCallback<? super T> callback) {
        this.callbacks.addCallback(callback);
    }

    public void addCallback(SuccessCallback<? super T> successCallback, FailureCallback failureCallback) {
        this.callbacks.addSuccessCallback(successCallback);
        this.callbacks.addFailureCallback(failureCallback);
    }

    protected final void done() {
        Object cause;
        try {
            Object ex = this.get();
            this.callbacks.success(ex);
            return;
        } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException var4) {
            cause = var4.getCause();
            if(cause == null) {
                cause = var4;
            }
        } catch (Throwable var5) {
            cause = var5;
        }

        this.callbacks.failure((Throwable)cause);
    }
}

```
注册监听，还维护了一个状态量的信息，很标准的写法，维护队列的添加和成功消息和失败消息的处理
```java
public class ListenableFutureCallbackRegistry<T> {

	private final Queue<SuccessCallback<? super T>> successCallbacks = new LinkedList<SuccessCallback<? super T>>();

	private final Queue<FailureCallback> failureCallbacks = new LinkedList<FailureCallback>();

	private State state = State.NEW;

	private Object result = null;

	private final Object mutex = new Object();


	/**
	 * Add the given callback to this registry.
	 * @param callback the callback to add
	 */
	public void addCallback(ListenableFutureCallback<? super T> callback) {
		Assert.notNull(callback, "'callback' must not be null");
		synchronized (this.mutex) {
			switch (this.state) {
				case NEW:
					this.successCallbacks.add(callback);
					this.failureCallbacks.add(callback);
					break;
				case SUCCESS:
					callback.onSuccess((T) this.result);
					break;
				case FAILURE:
					callback.onFailure((Throwable) this.result);
					break;
			}
		}
	}

	/**
	 * Add the given success callback to this registry.
	 * @param callback the success callback to add
	 * @since 4.1
	 */
	public void addSuccessCallback(SuccessCallback<? super T> callback) {
		Assert.notNull(callback, "'callback' must not be null");
		synchronized (this.mutex) {
			switch (this.state) {
				case NEW:
					this.successCallbacks.add(callback);
					break;
				case SUCCESS:
					callback.onSuccess((T) this.result);
					break;
			}
		}
	}

	/**
	 * Add the given failure callback to this registry.
	 * @param callback the failure callback to add
	 * @since 4.1
	 */
	public void addFailureCallback(FailureCallback callback) {
		Assert.notNull(callback, "'callback' must not be null");
		synchronized (this.mutex) {
			switch (this.state) {
				case NEW:
					this.failureCallbacks.add(callback);
					break;
				case FAILURE:
					callback.onFailure((Throwable) this.result);
					break;
			}
		}
	}

	/**
	 * Trigger a {@link ListenableFutureCallback#onSuccess(Object)} call on all
	 * added callbacks with the given result.
	 * @param result the result to trigger the callbacks with
	 */
	public void success(T result) {
		synchronized (this.mutex) {
			this.state = State.SUCCESS;
			this.result = result;
			while (!this.successCallbacks.isEmpty()) {
				this.successCallbacks.poll().onSuccess(result);
			}
		}
	}

	public void failure(Throwable ex) {
		synchronized (this.mutex) {
			this.state = State.FAILURE;
			this.result = ex;
			while (!this.failureCallbacks.isEmpty()) {
				this.failureCallbacks.poll().onFailure(ex);
			}
		}
	}

	private enum State {NEW, SUCCESS, FAILURE}

}

```