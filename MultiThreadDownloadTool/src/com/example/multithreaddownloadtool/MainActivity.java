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
				Toast.makeText(getApplicationContext(), "���سɹ�", 0).show();
				break;
			case ERROR:
				Toast.makeText(getApplicationContext(), "����ʧ��", 0).show();
				break;
			case THREAD_ERROR:
				Toast.makeText(getApplicationContext(), "����ʧ��", 0).show();
				break;
			default:
				break;
			}
		};
	};
	
	//�߳���
	private int threadCount=3;
	//ÿ����������Ĵ�С	
	private long blocksize;
	//�������е��̵߳ĸ���
	private int runningThreadCount;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		et_path=(EditText)findViewById(R.id.et_path);
		et_count=(EditText)findViewById(R.id.et_count);
		ll_container=(LinearLayout)findViewById(R.id.ll_container);		
	}

	//����
	public void download(View view){
		final String path=et_path.getText().toString().trim();
		if(TextUtils.isEmpty(path)){
			Toast.makeText(this, "�Բ���,����Ϊ��", 0).show();
			return ;
		}
		
		String count =et_count.getText().toString().trim();
		if(TextUtils.isEmpty(path)){
			Toast.makeText(this, "�Բ���,����Ϊ��", 0).show();
			return ;
		}
		threadCount=Integer.parseInt(count);
		
		//��վɵĽ�����
		ll_container.removeAllViews();		
		//��ӽ�����
		for(int j=0;j<threadCount;j++){
			ProgressBar pb=(ProgressBar)View.inflate(this, R.layout.pb, null);
			ll_container.addView(pb);
		}
		//��ʾ
		Toast.makeText(this, "��ʼ����", 0).show();
		
		new Thread(){
			public void run(){
				try {
					//����·��
					URL url=new URL(path);	
					
					HttpURLConnection conn=(HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");
					conn.setConnectTimeout(5000);
					int code=conn.getResponseCode();
					if(code==200){
						long size=conn.getContentLength();//�õ�����˷��ص��ļ���С
						System.out.println("�������ļ��Ĵ�С"+size);
						blocksize=size/threadCount;
						//�ڱ��ش�����С�ͷ��������ļ���Сһ�����ļ�
						File file=new File(Environment.getExternalStorageDirectory(),getFileName(path));
						RandomAccessFile raf=new RandomAccessFile(file, "rw");
						raf.setLength(size);
						//�������ɸ����̷ֱ߳�ȥ����
						runningThreadCount=threadCount;
						for(int i=1;i<=threadCount;i++){

							long startIndex=(i-1)*blocksize+1;
							long endIndex=i*blocksize;
							if(i==threadCount){
								endIndex=size;
							}
							System.out.println("�����߳�"+i+"����λ��"+startIndex+"~"+endIndex);
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
					//���ϴ����ص�λ�ü�����������
					
					if(positionFile.exists()&&positionFile.length()>0){//�ж��Ƿ��м�¼
						FileInputStream fis=new FileInputStream(positionFile); 
						
						BufferedReader br=new BufferedReader(new InputStreamReader(fis));
						//��ȡ��ǰ�߳��ϴ����صĴ�С�Ƕ���
						String lasttotalstr=br.readLine();
						int lastTotal=Integer.valueOf(lasttotalstr);
						System.out.println("�ϴ��߳�"+threadId+"���صĴ�СΪ"+lastTotal);
						startIndex+=lastTotal;
						total+=lastTotal;//�����ϴ����ص��ܴ�С
						fis.close();
					}
					
					conn.setRequestProperty("Range", "bytes="+startIndex+"-"+endIndex);
					conn.setConnectTimeout(5000);
					int code=conn.getResponseCode();
					System.out.println(code);
					InputStream is=conn.getInputStream();
					File file=new File(Environment.getExternalStorageDirectory(),getFileName(path));
					RandomAccessFile raf=new RandomAccessFile(file, "rw");
					
					//ָ���ļ���ʼд��λ��
					
					raf.seek(startIndex-1);
					System.out.println("��"+threadId+"���߳�д�ļ���λ��"+String.valueOf(startIndex-1));
					
					int len=0;
					byte[] buffer=new byte[1024*1024];
					
					//��ǰ�߳����ص��ܴ�С
					
					while((len=is.read(buffer))!=-1){//����������
						//FileOutputStream fos=new FileOutputStream(positionFile);
						RandomAccessFile rf=new RandomAccessFile(positionFile, "rwd");
						
						raf.write(buffer,0,len);
						total+=len;
						rf.write(String.valueOf(total).getBytes());
						rf.close();
					}
					
					is.close();
					raf.close();
					System.out.println("�߳�"+threadId+"�������");
					
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Message msg=Message.obtain();
					msg.what=THREAD_ERROR;
					handler.sendMessage(msg);
					
				}finally {
					//ֻ�����е��̶߳�������ϲ�ɾ���ļ�
					synchronized (MainActivity.class) {
						System.out.println("�߳�"+threadId+"�������");
						runningThreadCount--;
						if(runningThreadCount<1){
							System.out.println("���е��̶߳�������ϣ�ɾ����ʱ�ļ�");
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
	
	//��ȡ�ļ���
	private String getFileName(String path){
		int start=path.lastIndexOf("/");
		return path.substring(start);
	
	}

}
