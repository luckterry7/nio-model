package nioModel.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class MultiplexerTimeServer implements Runnable{
	private ServerSocketChannel servChannel;
	private Selector selector;
	private boolean stop;
	
	public MultiplexerTimeServer(int port) {
		try {
			selector = Selector.open();
			servChannel = ServerSocketChannel.open();
			// 设定channel为非阻塞
			servChannel.configureBlocking(false);
			
			//把channel绑定监听的ip地址
			servChannel.bind(new InetSocketAddress(port), 1024);
			
			//把channel注册到selector复用器中
			servChannel.register(selector, SelectionKey.OP_ACCEPT);
			
			System.out.println("The time server is started in port :" + port);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}


	
	
	@Override
	public void run() {
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
	
	
	private void handleInput(SelectionKey key) throws IOException{
		if(key.isValid()){
			//当key是准备accept状态时
			if(key.isAcceptable()){
				ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
				SocketChannel sc = ssc.accept();
				sc.configureBlocking(false);
				//把socketChannel注册到selector
				sc.register(selector, SelectionKey.OP_READ);
			}
			
			if(key.isReadable()){
				SocketChannel sc = (SocketChannel) key.channel();
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
					System.out.println("The time server receive message :" + body);
					
					String currentTime = "query time" .equalsIgnoreCase(body)
											? new java.util.Date(System.currentTimeMillis()).toString()
											: "Bad query";
					doWrite(sc,currentTime);
				} else if(readBytes < 0){//链路已经关闭，需要关闭socketChannel释放资源
					key.cancel();
					sc.close();
				}else //没有读到字节，属于正常情况，忽略
					;
			}
		}
	}
	
	private void doWrite(SocketChannel sc , String resp) throws IOException{
		if(resp != null && resp.trim().length() > 0){
			byte[] bytes = resp.getBytes();
			ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
			//把bytes放入缓存区
			writeBuffer.put(bytes);
			writeBuffer.flip();
			sc.write(writeBuffer);
		}
	}
}
