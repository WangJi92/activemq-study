package activemqspring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * descrption: MessageListenerAdapter除了会自动的把一个普通Java类当做MessageListener来处理接收到的消息之外
 * authohr: wangji
 * date: 2017-09-27 15:46
 */
public class SpringReceiverAdapterLinstenerTransactionManager {

    public void handleMessage(String message) {
        System.out.println("ConsumerListener通过handleMessage接收到一个纯文本消息，消息内容是：" + message);
    }
    public void receiveMessage(String message) {
       /* 消息转换器自动识别接收消息的类型，然后进行相应的转换*/
        System.out.println("ConsumerListener通过receiveMessage接收到一个纯文本消息，消息内容是：" + message);
        // http://blog.csdn.net/lsm135/article/details/74945116 出现6次异常之后就被消费不可回滚。
        throw new RuntimeException("出现异常");
    }

    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("jmx/application-jmx-listener-transactionManager.xml");
    }
}
