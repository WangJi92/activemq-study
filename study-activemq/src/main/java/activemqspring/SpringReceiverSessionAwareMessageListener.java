package activemqspring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jms.listener.SessionAwareMessageListener;

import javax.jms.*;

/**
 * descrption:接收消息的同时可以发送
 * authohr: wangji
 * date: 2017-09-27 15:22
 */
@Slf4j
public class SpringReceiverSessionAwareMessageListener implements SessionAwareMessageListener<TextMessage> {

    private Destination destination;

    public void onMessage(TextMessage message, Session session) throws JMSException {

        System.out.println("消息内容是：" + message.getText());
        //通过基本的API，进行处理，创建一个生产者，然后发送消息
        MessageProducer producer = session.createProducer(destination);
        Message textMessage = session.createTextMessage("SpringReceiverSessionAwareMessageListener。。。");
        producer.send(textMessage);
    }

    public Destination getDestination() {
        return destination;
    }

    public void setDestination(Destination destination) {
        this.destination = destination;
    }

    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("jmx/application-jmx-listener2.xml");
    }
}
