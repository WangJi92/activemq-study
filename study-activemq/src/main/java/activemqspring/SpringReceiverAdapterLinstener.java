package activemqspring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * descrption: MessageListenerAdapter除了会自动的把一个普通Java类当做MessageListener来处理接收到的消息之外
 * authohr: wangji
 * date: 2017-09-27 15:46
 */
public class SpringReceiverAdapterLinstener {

    public void handleMessage(String message) {
        System.out.println("ConsumerListener通过handleMessage接收到一个纯文本消息，消息内容是：" + message);
    }
    /*
    默认的SimpleMessageConverter进行的转换处理如下
    public Message toMessage(Object object, Session session) throws JMSException, MessageConversionException {
        if(object instanceof Message) {
            return (Message)object;
        } else if(object instanceof String) {
            return this.createMessageForString((String)object, session);
        } else if(object instanceof byte[]) {
            return this.createMessageForByteArray((byte[])((byte[])object), session);
        } else if(object instanceof Map) {
            return this.createMessageForMap((Map)object, session);
        } else if(object instanceof Serializable) {
            return this.createMessageForSerializable((Serializable)object, session);
        } else {
            throw new MessageConversionException("Cannot convert object of type [" + ObjectUtils.nullSafeClassName(object) + "] to JMS message. Supported message " + "payloads are: String, byte array, Map<String,?>, Serializable object.");
        }
    }*/
    public void receiveMessage(String message) {
       /* 消息转换器自动识别接收消息的类型，然后进行相应的转换*/
        System.out.println("ConsumerListener通过receiveMessage接收到一个纯文本消息，消息内容是：" + message);
    }

    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("jmx/application-jmx-listener3.xml");
    }
}
