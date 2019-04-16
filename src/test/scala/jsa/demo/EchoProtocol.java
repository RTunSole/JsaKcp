package jsa.demo;

/**
 * Don't say anything,just do it !
 * Created by atunso.liu on 2018/9/24.
 */
import java.nio.channels.SelectionKey;
import java.io.IOException;

public interface EchoProtocol {
    void handleAccept(SelectionKey key) throws IOException;
    void handleRead(SelectionKey key) throws IOException;
    void handleWrite(SelectionKey key) throws IOException;
}
