package activemqspring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

/**
 * descrption:spring template发送
 * authohr: wangji
 * date: 2017-09-27 13:48
 */
@Slf4j
public class SpringSender {
    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("jmx/application-jmx.xml");
        JmsTemplate template = (JmsTemplate) applicationContext.getBean("jmsTemplate");
        Destination destination = (Destination) applicationContext.getBean("destination");
        template.send(destination, new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                return session.createTextMessage("发送消息：Hello ActiveMQ Text Message2！");
            }
        });
       log.info("成功发送了一条JMS消息");
    }
}
