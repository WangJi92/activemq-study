package activemqspring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jms.core.JmsTemplate;

import javax.jms.Destination;
import javax.jms.TextMessage;

/**
 * descrption:接收消息
 * authohr: wangji
 * date: 2017-09-27 14:02
 */
@Slf4j
public class SpringReceiver {

    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("jmx/application-jmx.xml");
        Destination destination = (Destination) applicationContext.getBean("destination");
        JmsTemplate jmsTemplate = (JmsTemplate) applicationContext.getBean("jmsTemplate");
        while (true) {
            try {
                TextMessage txtmsg = (TextMessage) jmsTemplate
                        .receive(destination);
                if (null != txtmsg) {
                   log.info("[message] " + txtmsg);
                   log.info("[message] 收到消息内容为: "
                            + txtmsg.getText());
                } else
                    break;
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
