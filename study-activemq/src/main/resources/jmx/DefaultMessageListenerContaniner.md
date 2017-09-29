#DefaultMessageListenerContainer的流程

##看图，这个直接配置为Bean，肯定使用了Spring配置的通过初始化Bean来执行一些操作
JmsAccessor（JMS访问器） 实现了InitializingBean，所以程序的入口在这里
```
public abstract class JmsAccessor implements InitializingBean {
    private static final Constants sessionConstants = new Constants(Session.class);
    protected final Log logger = LogFactory.getLog(this.getClass());
    private ConnectionFactory connectionFactory;//连接工厂
    private boolean sessionTransacted = false;//事务控制
    private int sessionAcknowledgeMode = 1;//应答模式
    public void afterPropertiesSet() {
            if(this.getConnectionFactory() == null) {
                throw new IllegalArgumentException("Property \'connectionFactory\' is required");
            }
        } 
    
  }

```
AbstractJmsListeningContainer覆盖了afterPropertiesSet方法，添加了一些参数校验和一个初始化方法，中使用了很多的模板方法，钩子函数给子类去调用
```
public abstract class AbstractJmsListeningContainer extends JmsDestinationAccessor implements BeanNameAware, DisposableBean, SmartLifecycle {
    private String clientId;
    private boolean autoStartup = true;//自动开始，生命周期函数
    private int phase = 2147483647;
    private String beanName;
    private Connection sharedConnection;
    private boolean sharedConnectionStarted = false;
    protected final Object sharedConnectionMonitor = new Object();
    private boolean active = false;//运行状态的函数
    private boolean running = false;
    private final List<Object> pausedTasks = new LinkedList();
    protected final Object lifecycleMonitor = new Object();
    public void afterPropertiesSet() {//覆盖父类的方法
            super.afterPropertiesSet();
            this.validateConfiguration();
            this.initialize();
    }
    protected void validateConfiguration() {//参数校验留个子类钩子去校验，有序性得到保障
     
    }
    //当前类实现了一些生命周期的函数，被子类覆盖，留下钩子函数doInitialize
    public void initialize() throws JmsException {
            try {
                Object ex = this.lifecycleMonitor;
                synchronized(this.lifecycleMonitor) {
                    this.active = true;
                    this.lifecycleMonitor.notifyAll();
                }
    
                this.doInitialize();
            } catch (JMSException var6) {
                Object var2 = this.sharedConnectionMonitor;
                synchronized(this.sharedConnectionMonitor) {
                    ConnectionFactoryUtils.releaseConnection(this.sharedConnection, this.getConnectionFactory(), this.autoStartup);
                    this.sharedConnection = null;
                }
    
                throw this.convertJmsAccessException(var6);
            }
        }
         //这个是抽象函数，子类必须实现，由于前面的模板方法的有序性，这里执行顺序得到保障。      
         protected abstract void doInitialize() throws JMSException;
  }
```
子类AbstractMessageListenerContainer实现了之前的AbstractJmsListeningContainer并执行父类的一些参数校验工作，并实现了MessageListenerContainer这个接口，专门为接收消息的监听而设置的
```
public interface MessageListenerContainer extends SmartLifecycle {
    void setupMessageListener(Object var1);//消息到了，执行具体任务的消息处理函数

    MessageConverter getMessageConverter();//消息转换类的信息

    boolean isPubSubDomain();//是不是Topic
}
```
AbstractMessageListenerContainer
```
public abstract class AbstractMessageListenerContainer extends AbstractJmsListeningContainer implements MessageListenerContainer {
    //jMS2.0支持的属性createSharedConsumer
    //非共享非持久订阅。这些在 JMS 1.1 和 JMS 2.0 中均可用，是用 createConsumer 创建的。它们只能有一个使用者。设置客户端标识符是可选的。
    //非共享持久订阅。这些在 JMS 1.1 和 JMS 2.0 中均可用，是用 createDurableSubscriber 或（仅在 JMS 2.0 中）createDurableConsumer 创建的。它们只能有一个使用者。设置客户端标识符是强制性的，订阅由订阅名和客户端标识符的组合来标识。
    //共享非持久订阅.这些只在 JMS 2.0 中可用，是用 createSharedConsumer 创建的。它们可以有任何数量的使用者。设置客户端标识符是可选的。如果设置的话，订阅由订阅名和客户端标识符的组合来标识。
    //共享持久订阅。这些只在 JMS 2.0 中可用，是用 createSharedDurableConsumer 创建的。它们可以有任何数量的使用者。设置客户端标识符是可选的。如果设置的话，订阅由订阅名和客户端标识符的组合来标识。
    private static final Method createSharedConsumerMethod = ClassUtils.getMethodIfAvailable(Session.class, "createSharedConsumer", new Class[]{Topic.class, String.class, String.class});
    private static final Method createSharedDurableConsumerMethod = ClassUtils.getMethodIfAvailable(Session.class, "createSharedDurableConsumer", new Class[]{Topic.class, String.class, String.class});
    private volatile Object destination;//目的地
    private volatile String messageSelector;//筛选器
    private volatile Object messageListener;//监听者
    private boolean subscriptionDurable = false;//刚刚的概念持久？共享？
    private boolean subscriptionShared = false;
    private String subscriptionName;//订阅名称
    private boolean pubSubNoLocal = false;//限制消费者只能接收和自己相同的连接（Connection）所发布的消息
    private MessageConverter messageConverter;//消息转换器
    private ExceptionListener exceptionListener;//异常监听器
    private ErrorHandler errorHandler;//异常处理
    private boolean exposeListenerSession = true;//暴露session
    private boolean acceptMessagesWhileStopping = false;//接受消息而停止
    //覆盖父类校验目的地
    protected void validateConfiguration() {
        if(this.destination == null) {
            throw new IllegalArgumentException("Property \'destination\' or \'destinationName\' is required");
        }
    }
    
 }
```
AbstractPollingMessageListenerContainer（抽象轮询处理）覆盖了AbstractJmsListeningContainer的initialize（）方法
```
public abstract class AbstractPollingMessageListenerContainer extends AbstractMessageListenerContainer {
    public static final long DEFAULT_RECEIVE_TIMEOUT = 1000L;
    private final AbstractPollingMessageListenerContainer.MessageListenerContainerResourceFactory transactionalResourceFactory = new AbstractPollingMessageListenerContainer.MessageListenerContainerResourceFactory();
    private boolean sessionTransactedCalled = false;
    private PlatformTransactionManager transactionManager;//事务控制
    private DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
    private long receiveTimeout = 1000L;//接收超时
    private volatile Boolean commitAfterNoMessageReceived;
    
    //这里设置两个事务控制的参数为啥？
    public void setSessionTransacted(boolean sessionTransacted) {
        super.setSessionTransacted(sessionTransacted);
        this.sessionTransactedCalled = true;
    }
    //这里的初始化完了，又跑到了AbstractJmsListeningContainer的initialize（）方法中去
    //调用抽象函数doInitialize 看下文DefaultMessageListenerContainer中实现了这个函数
    public void initialize() {
        //设置是否使用事务的参数
        if(!this.sessionTransactedCalled && 
          this.transactionManager instanceof ResourceTransactionManager && 
          !TransactionSynchronizationUtils.
           sameResourceFactory(
           (ResourceTransactionManager)this.transactionManager, this.getConnectionFactory())) {
            super.setSessionTransacted(true);
        }

        if(this.transactionDefinition.getName() == null) {
            this.transactionDefinition.setName(this.getBeanName());
        }

        super.initialize();
    }
    
    
    //封装这个方法就是为了方便调用获取资源信息，不管你是共享还是不是共享的，方便调用，可能是提取出来的吧！
    private class MessageListenerContainerResourceFactory implements ResourceFactory {
            private MessageListenerContainerResourceFactory() {
            }
    
            public Connection getConnection(JmsResourceHolder holder) {
                return AbstractPollingMessageListenerContainer.this.getConnection(holder);
            }
    
            public Session getSession(JmsResourceHolder holder) {
                return AbstractPollingMessageListenerContainer.this.getSession(holder);
            }
    
            public Connection createConnection() throws JMSException {
                if(AbstractPollingMessageListenerContainer.this.sharedConnectionEnabled()) {
                    Connection sharedCon = AbstractPollingMessageListenerContainer.this.getSharedConnection();
                    return (new SingleConnectionFactory(sharedCon)).createConnection();
                } else {
                    return AbstractPollingMessageListenerContainer.this.createConnection();
                }
            }
    
            public Session createSession(Connection con) throws JMSException {
                return AbstractPollingMessageListenerContainer.this.createSession(con);
            }
    
            public boolean isSynchedLocalTransactionAllowed() {
                return AbstractPollingMessageListenerContainer.this.isSessionTransacted();
            }
     }
    
 }

```
DefaultMessageListenerContainer 最后的实现类了
```
public class DefaultMessageListenerContainer extends AbstractPollingMessageListenerContainer {
    public static final String DEFAULT_THREAD_NAME_PREFIX = ClassUtils.getShortName(DefaultMessageListenerContainer.class) + "-";
    public static final long DEFAULT_RECOVERY_INTERVAL = 5000L;
    public static final int CACHE_NONE = 0;
    public static final int CACHE_CONNECTION = 1;
    public static final int CACHE_SESSION = 2;
    public static final int CACHE_CONSUMER = 3;
    public static final int CACHE_AUTO = 4;//缓存的界别
    private static final Constants constants = new Constants(DefaultMessageListenerContainer.class);
    private Executor taskExecutor;//线程池
    private BackOff backOff = this.createDefaultBackOff(5000L);
    private int cacheLevel = 4;
    private int concurrentConsumers = 1;
    private int maxConcurrentConsumers = 1;//最大的消费者
    private int maxMessagesPerTask = -2147483648;
    private int idleConsumerLimit = 1;
    private int idleTaskExecutionLimit = 1;//空闲任务限制
    //当前的执行任务的线程
    private final Set<DefaultMessageListenerContainer.AsyncMessageListenerInvoker> scheduledInvokers = new HashSet();
   //存活的数量
    private int activeInvokerCount = 0;
    private int registeredWithDestination = 0;
    private volatile boolean recovering = false;
    //停止调用方法的回调
    private Runnable stopCallback;
    private Object currentRecoveryMarker = new Object();
    private final Object recoveryMonitor = new Object();
    
    //首先设置缓存类别，然后创建一个线程池！
    public void initialize() {
            if(this.cacheLevel == 4) {
                this.cacheLevel = this.getTransactionManager() != null?0:3;
            }
    
            Object var1 = this.lifecycleMonitor;
            synchronized(this.lifecycleMonitor) {
                if(this.taskExecutor == null) {
                    this.taskExecutor = this.createDefaultTaskExecutor();
                } else if(this.taskExecutor instanceof SchedulingTaskExecutor && ((SchedulingTaskExecutor)this.taskExecutor).prefersShortLivedTasks() && this.maxMessagesPerTask == -2147483648) {
                    this.maxMessagesPerTask = 10;
                }
            }
    
            super.initialize();
        }
     //这里是重点根据线程的数量执行任务   
      protected void doInitialize() throws JMSException {
            Object var1 = this.lifecycleMonitor;
            synchronized(this.lifecycleMonitor) {
                for(int i = 0; i < this.concurrentConsumers; ++i) {
                    this.scheduleNewInvoker();
                }
    
            }
        }
    
    
```

http://www.cnblogs.com/wade-luffy/p/6090933.html



