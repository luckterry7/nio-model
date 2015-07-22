package nioModel.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class TimeClientHandle implements Runnable{
	private String host ;
	private int port;
	private Selector selector;
	private SocketChannel socketchannel;
	private boolean stop;
	
	public TimeClientHandle(String host,int port) {
		this.host = host;
		this.port = port;
		try {
			selector = Selector.open();
			socketchannel = SocketChannel.open();
			socketchannel.configureBlocking(false);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	@Override
	public void run() {
		try {
			doConnect();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		while(!stop){
			try {
				//设定selector休眠时间为1s
				selector.select(1000);
				Set<SelectionKey> selectedKeys = selector.selectedKeys();
				//当有处于就绪状态下的channel，selector会返回就绪channel的select-key集合
				Iterator<SelectionKey> it = selectedKeys.iterator();
				SelectionKey key = null;
				while(it.hasNext()){
					key = it.next();
					it.remove();
					try{
						handleInput(key);
					}catch (Exception e){
						if (key != null){
							key.channel();
							if(key.channel() != null){
								key.channel().close();
							}
						}
					}
				}
						
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		//最后，关闭多路复用器，多余复用器关闭后，所有注册在什么的channel和pipe等资源都会自动去注册关闭
		if(selector != null){
			try {
				selector.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}

	private void doConnect() throws IOException {
		if(socketchannel.connect(new InetSocketAddress(host,port))){
			socketchannel.register(selector, SelectionKey.OP_READ);
			doWrite(socketchannel);
		}else {
			socketchannel.register(selector, SelectionKey.OP_CONNECT);
		}
	}

	private void doWrite(SocketChannel sc) throws IOException {
		byte[] bytes = "query time".getBytes();
		ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
		//把bytes放入缓存区
		writeBuffer.put(bytes);
		writeBuffer.flip();
		sc.write(writeBuffer);
		if(!writeBuffer.hasRemaining()){
			System.out.println("send  message 2 server succeed");
		}
	}

	
	private void handleInput(SelectionKey key) throws IOException{
		if(key.isValid()){
			SocketChannel sc = (SocketChannel) key.channel();
			if(key.isConnectable()){//判断channel是否已经连接成功
				if(sc.finishConnect()){//对连接的结果进行判断,如果是true表示客户端连接成功
					sc.register(selector, SelectionKey.OP_READ);
					doWrite(sc);
				}else{
					//连接失败，进程退出
					System.exit(1);
				}
				
			}
			
			if(key.isReadable()){
				ByteBuffer readBuffer = ByteBuffer.allocate(1024);
				int readBytes = sc.read(readBuffer);
				
				//返回值大于0，读到字节了，对字节进行编码
				if(readBytes > 0){
					readBuffer.flip();
					
					//新建一个bytes数组，用于保存接收到的数据
					byte[] bytes = new byte[readBuffer.remaining()];
					//把接收到的数据放入bytes中
					readBuffer.get(bytes);
					String body = new String(bytes,"UTF-8");
					System.out.println("The time client receive message : time is " + body);
					this.stop = true;
				} else if(readBytes < 0){//链路已经关闭，需要关闭socketChannel释放资源
					key.cancel();
					sc.close();
				}else //没有读到字节，属于正常情况，忽略
					;
			}
		}
	}
}
