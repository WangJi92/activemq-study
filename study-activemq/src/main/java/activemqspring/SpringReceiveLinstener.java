package activemqspring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

/**
 * descrption:简化的线程去接收消费者信息的请求
 * authohr: wangji
 * date: 2017-09-27 14:43
 * //http://elim.iteye.com/blog/1893676 这个地址讲解的比较的详细
 */
@Slf4j
public class SpringReceiveLinstener implements MessageListener {
    /**
     * 启动一个线程去接收消息，有消息调用这里，主要的逻辑信息看这里org.springframework.jms.listener.DefaultMessageListenerContainer
     * @param message
     */
    public void onMessage(Message message) {
        TextMessage textMsg = (TextMessage) message;
        System.out.println("接收到一个纯文本消息。");
        try {
           log.info("消息内容是：" + textMsg.getText());
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("jmx/application-jmx-listener.xml");
    }
}
