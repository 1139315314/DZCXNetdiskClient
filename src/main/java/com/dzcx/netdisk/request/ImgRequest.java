package com.dzcx.netdisk.request;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;

import com.dzcx.netdisk.Entrance;
import com.dzcx.netdisk.entity.MyConfig;
import com.dzcx.netdisk.entity.Request;
import com.dzcx.netdisk.util.implement.ToolsImp;
import com.google.gson.Gson;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import net.coobird.thumbnailator.Thumbnails;


public class ImgRequest extends Service<Image> {

	private MyConfig config = Entrance.config;
	private InputStream is;
	private OutputStream os;
	
	private String key, value;
	private Socket socket;
	private String ip; int port;
	private boolean isCompressImg = false;
	private DecimalFormat format = new DecimalFormat("#,###");

	/**
	 * isCompressImg 为 true 时不执行压缩
	 * 
	 * @param key
	 * @param value
	 * @param isCompressImg
	 * @param
	 */
	public ImgRequest(String key, String value, boolean isCompressImg) {
		this.key = key;
		this.value = value;
		this.isCompressImg = isCompressImg;
		ip = config.getIp().toString();
		port = Integer.valueOf(config.getPortPublic());
	}

	protected Task<Image> createTask() {
		return new Task<Image>() {
			
			private int reTry = 0;
			private Image img = null;
			
			protected Image call() throws Exception {
				updateMessage("正在连接");
				return getImg();
			}
			
			private Image getImg() throws Exception {
				try {
					if (reTry == 3) return null; // 3 次重连失败
					socket = new Socket();
					socket.connect(new InetSocketAddress(ip, port), 1000);
				} catch (SocketTimeoutException e) {
					reTry++;
					return getImg();
				}
				if (socket.isConnected()) {
					Request request = new Request();
					request.setKey(key);
					request.setValue(value);
					os = socket.getOutputStream();
					os.write((new Gson().toJson(request) + "\r\n").getBytes("UTF-8"));
					// 获取文件大小
					is = socket.getInputStream();
					BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
					String result = br.readLine(), onload = "加载中";
					if (result.startsWith("size")) {
						long fileSize = Integer.valueOf(result.substring(4));
						os = socket.getOutputStream();
						os.write("ready\r\n".getBytes("UTF-8"));
						is = socket.getInputStream();
						// 克隆输入流
						ByteArrayOutputStream cloneBAOS = new ByteArrayOutputStream();
						byte[] buffer = new byte[4096];
						int l = 0;
						double progress = 0;
						while ((l = is.read(buffer)) > -1) {
							cloneBAOS.write(buffer, 0, l);
							cloneBAOS.flush();
							progress += l;
							updateMessage(onload + formatOnload(progress) + " / " + formatOnload(fileSize));
							updateProgress(progress / fileSize, 1);
						}
						cloneBAOS.flush();
						// 接收图片流
						if (isCompressImg) { // 直接收图
							InputStream isOut = new ByteArrayInputStream(cloneBAOS.toByteArray());
							img = new Image(isOut);
							isOut.close();
						} else { // 压缩收图
							InputStream isIfx = new ByteArrayInputStream(cloneBAOS.toByteArray());
							InputStream isOutx = new ByteArrayInputStream(cloneBAOS.toByteArray());
							img = new Image(isIfx);
							ByteArrayOutputStream baos = new ByteArrayOutputStream();
							if (img.getWidth() < img.getHeight()) {
								Thumbnails.of(isOutx).width(128).toOutputStream(baos);
							} else {
								Thumbnails.of(isOutx).height(128).toOutputStream(baos);
							}
							img = new Image(os2is(baos));
							isOutx.close();
							isIfx.close();
							baos.close();
							System.gc();
						}
						cloneBAOS.close();
						os.flush();
						os.close();
					}
				}
				return img;
			}
		};
	}
	
	private String formatOnload(double v) {
		return new ToolsImp().storageFormat(v, format);
	}

	// 输出转输入
	private ByteArrayInputStream os2is(OutputStream out) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos = (ByteArrayOutputStream) out;
		ByteArrayInputStream swapStream = new ByteArrayInputStream(baos.toByteArray());
		return swapStream;
	}
}