package com.example.multithreaddownloadtool;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

import javax.security.auth.PrivateCredentialPermission;

import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends Activity {

	protected static final int ERROR = 0;
	public static final int THREAD_ERROR = 1;
	public static final int DOWNLOAD_SUCCESS = 2;
	private EditText et_path;
	private EditText et_count;
	private LinearLayout ll_container;
	
	private Handler handler=new Handler(){
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case DOWNLOAD_SUCCESS:
				Toast.makeText(getApplicationContext(), "下载成功", 0).show();
				break;
			case ERROR:
				Toast.makeText(getApplicationContext(), "下载失败", 0).show();
				break;
			case THREAD_ERROR:
				Toast.makeText(getApplicationContext(), "下载失败", 0).show();
				break;
			default:
				break;
			}
		};
	};
	
	//线程数
	private int threadCount=3;
	//每个下载区块的大小	
	private long blocksize;
	//正在运行的线程的个数
	private int runningThreadCount;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		et_path=(EditText)findViewById(R.id.et_path);
		et_count=(EditText)findViewById(R.id.et_count);
		ll_container=(LinearLayout)findViewById(R.id.ll_container);		
	}

	//下载
	public void download(View view){
		final String path=et_path.getText().toString().trim();
		if(TextUtils.isEmpty(path)){
			Toast.makeText(this, "对不起,不能为空", 0).show();
			return ;
		}
		
		String count =et_count.getText().toString().trim();
		if(TextUtils.isEmpty(path)){
			Toast.makeText(this, "对不起,不能为空", 0).show();
			return ;
		}
		threadCount=Integer.parseInt(count);
		
		//清空旧的进度条
		ll_container.removeAllViews();		
		//添加进度条
		for(int j=0;j<threadCount;j++){
			ProgressBar pb=(ProgressBar)View.inflate(this, R.layout.pb, null);
			ll_container.addView(pb);
		}
		//提示
		Toast.makeText(this, "开始下载", 0).show();
		
		new Thread(){
			public void run(){
				try {
					//下载路径
					URL url=new URL(path);	
					
					HttpURLConnection conn=(HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");
					conn.setConnectTimeout(5000);
					int code=conn.getResponseCode();
					if(code==200){
						long size=conn.getContentLength();//得到服务端返回的文件大小
						System.out.println("服务器文件的大小"+size);
						blocksize=size/threadCount;
						//在本地创建大小和服务器上文件大小一样的文件
						File file=new File(Environment.getExternalStorageDirectory(),getFileName(path));
						RandomAccessFile raf=new RandomAccessFile(file, "rw");
						raf.setLength(size);
						//开启若干个子线程分别去下载
						runningThreadCount=threadCount;
						for(int i=1;i<=threadCount;i++){

							long startIndex=(i-1)*blocksize+1;
							long endIndex=i*blocksize;
							if(i==threadCount){
								endIndex=size;
							}
							System.out.println("开启线程"+i+"下载位置"+startIndex+"~"+endIndex);
							new downloadThread(path, i, startIndex, endIndex).start();
							
						}
					}
					conn.disconnect();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Message msg=Message.obtain();
					msg.what=ERROR;
					handler.sendMessage(msg);
				}				
			};
		}.start();
		
		private class downloadThread extends Thread {
			
			private int threadId;
			private long startIndex;
			private long endIndex;
			private String path;

			public downloadThread(String path, int threadId, long startIndex,
					long endIndex) {
				this.path = path;
				this.threadId = threadId;
				this.startIndex = startIndex; 
				this.endIndex = endIndex;
			}
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {
					int total=0;
					File positionFile=new File(Environment.getExternalStorageDirectory(),getFileName(path)+threadId+".txt");

					URL url=new URL(path);	
					
					HttpURLConnection conn=(HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");
					//从上次下载的位置继续下载数据
					
					if(positionFile.exists()&&positionFile.length()>0){//判断是否有记录
						FileInputStream fis=new FileInputStream(positionFile); 
						
						BufferedReader br=new BufferedReader(new InputStreamReader(fis));
						//获取当前线程上次下载的大小是多少
						String lasttotalstr=br.readLine();
						int lastTotal=Integer.valueOf(lasttotalstr);
						System.out.println("上次线程"+threadId+"下载的大小为"+lastTotal);
						startIndex+=lastTotal;
						total+=lastTotal;//加上上次下载的总大小
						fis.close();
					}
					
					conn.setRequestProperty("Range", "bytes="+startIndex+"-"+endIndex);
					conn.setConnectTimeout(5000);
					int code=conn.getResponseCode();
					System.out.println(code);
					InputStream is=conn.getInputStream();
					File file=new File(Environment.getExternalStorageDirectory(),getFileName(path));
					RandomAccessFile raf=new RandomAccessFile(file, "rw");
					
					//指定文件开始写的位置
					
					raf.seek(startIndex-1);
					System.out.println("第"+threadId+"个线程写文件的位置"+String.valueOf(startIndex-1));
					
					int len=0;
					byte[] buffer=new byte[1024*1024];
					
					//当前线程下载的总大小
					
					while((len=is.read(buffer))!=-1){//读到缓冲区
						//FileOutputStream fos=new FileOutputStream(positionFile);
						RandomAccessFile rf=new RandomAccessFile(positionFile, "rwd");
						
						raf.write(buffer,0,len);
						total+=len;
						rf.write(String.valueOf(total).getBytes());
						rf.close();
					}
					
					is.close();
					raf.close();
					System.out.println("线程"+threadId+"下载完毕");
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Message msg=Message.obtain();
					msg.what=THREAD_ERROR;
					handler.sendMessage(msg);
					
				}finally {
					//只有所有的线程都下载完毕才删除文件
					synchronized (MainActivity.class) {
						System.out.println("线程"+threadId+"工作完毕");
						runningThreadCount--;
						if(runningThreadCount<1){
							System.out.println("所有的线程都工作完毕，删除临时文件");
							for(int i=1;i<=threadCount;i++){
								File f=new File(Environment.getExternalStorageDirectory(),getFileName(path)+i+".txt");							
								f.delete();
							}
							Message msg=Message.obtain();
							msg.what=DOWNLOAD_SUCCESS;
							handler.sendMessage(msg);
						}					
					}
				}
			}
		}
		
	}
	
	//获取文件名
	private String getFileName(String path){
		int start=path.lastIndexOf("/");
		return path.substring(start);
	
	}

}
