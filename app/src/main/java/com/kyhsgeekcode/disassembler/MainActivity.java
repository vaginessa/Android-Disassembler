package com.kyhsgeekcode.disassembler;

import android.*;
import android.accounts.*;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.*;
import android.database.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.support.v4.widget.*;
import android.support.v7.app.*;
import android.util.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import capstone.*;
import com.codekidlabs.storagechooser.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import nl.lxtreme.binutils.elf.*;
import org.boris.pecoff4j.*;
import org.boris.pecoff4j.io.*;
import com.codekidlabs.storagechooser.utils.*;
import com.kyhsgeekcode.disassembler.ProjectManager.*;
import java.nio.channels.*;
import com.evrencoskun.tableview.*;


public class MainActivity extends AppCompatActivity implements Button.OnClickListener, ProjectManager.OnProjectOpenListener
{
	public DatabaseHelper getDb()
	{
		return db;
	}
	@Override
	public void onOpen(ProjectManager.Project proj)
	{
		// TODO: Implement this method
		db=new DatabaseHelper(this,ProjectManager.createPath(proj.name)+"disasm.db");
		OnChoosePath(proj.oriFilePath);
		currentProject=proj;
		setting=getSharedPreferences(SETTINGKEY,MODE_PRIVATE);
		editor=setting.edit();
		editor.putString(LASTPROJKEY,proj.name);
		String det=proj.getDetail();
		if(!"".equals(det))
		{
			etDetails.setText(det);
		}
		
		File dir=new File(projectManager.RootFile,currentProject.name+"/");
		Log.d(TAG,"dirpath="+dir.getAbsolutePath());
		File file=new File(dir, "Disassembly.raw");
		if(file.exists()){
			try
			{
				FileInputStream fis = new FileInputStream(file);
				ObjectInputStream ois = new ObjectInputStream(fis);
				disasmResults = (ArrayList<DisasmResult>)ois.readObject();
				ois.close();
			}
			catch (ClassNotFoundException e)
			{
				AlertError("Error loading from raw",e);
			}
			catch (IOException e)
			{
				AlertError("Error loading from raw",e);
			}
		}
		else
		{
			disasmResults=(ArrayList<DisasmResult>) db.getAll();
		}
		if(disasmResults!=null)
		{
			int len=disasmResults.size();
			for(int i=0;i<len;++i)
			{
				adapter.addItem(disasmResults.get(i));
				adapter.notifyDataSetChanged();
			}
		}else{
			disasmResults=new ArrayList<>();
		}
		return ;
	}
	
	private static final int REQUEST_SELECT_FILE = 123;
	private static final int BULK_SIZE = 1024;
	private static final String SETTINGKEY="setting";
	private static final String LASTPROJKEY = "lastProject";
	String fpath;
	byte[] filecontent=null;
	ELFUtil elfUtil;
	SharedPreferences setting;
	SharedPreferences.Editor editor;
	SharedPreferences settingPath;
	
	private static final String TAG="Disassembler";
	private static final String RATIONALSETTING = "showRationals";
	boolean showAddress=true;
	boolean showLabel=true;
	boolean showBytes=true;
	boolean showInstruction=true;
	boolean showCondition=true;
	boolean showOperands=true;
	boolean showComment=true;
	private CustomDialog mCustomDialog;

	private ListViewAdapter adapter;

	private ListView listview;
	ArrayList<DisasmResult> disasmResults=new ArrayList<>();

	private TableLayout tlDisasmTable;

	private EditText etDetails;
	//ViewPager vp;
	TabHost tabHost;
	FrameLayout frameLayout;
	LinearLayout tab1,tab2;

	private EditText etFilename;

	private Button btSavDisasm;

	private Button btDisasm;

	private Button btShowDetails;

	private Button btSavDit;

	private Button btAbort;

	private String[] mProjNames;
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;

	private NotificationManager mNotifyManager;

	private Notification.Builder mBuilder;

	boolean instantMode;

	private long instantEntry;

	Thread workerThread;

	private Capstone cs;

	private String EXTRA_NOTIFICATION_ID;

	private String ACTION_SNOOZE;

	private ProjectManager projectManager;

	private ProjectManager.Project currentProject;
	
	//private SymbolTableAdapter symAdapter;
	
	//private TableView tvSymbols;
	
	private ListView lvSymbols;
	
	private SymbolListAdapter symbolLvAdapter;
	
	DatabaseHelper db;
	//DisasmIterator disasmIterator;
	@Override
	public void onClick(View p1)
	{
		Button btn=(Button)p1;
		switch (btn.getId())
		{
			case R.id.selFile:
				showFileChooser();
				break;
			case R.id.btnDisasm:
				if (filecontent == null)
				{
					AlertSelFile();
					return;
				}
				final List<String> ListItems = new ArrayList<>();
				ListItems.add("Instant mode");
				ListItems.add("Persist mode");
				ShowSelDialog(ListItems,"Disassemble as...",new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int pos)
						{
							//String selectedText = items[pos].toString();
							if (pos == 0)
							{
								instantMode = true;
								ListItems.clear();
								ListItems.add("Entry point");
								ListItems.add("Custom address");
								ShowSelDialog(ListItems,"Start from...",new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog2, int pos)
										{						
											if (pos == 0)
											{
												instantEntry = elfUtil.getEntryPoint();
												DisassembleInstant();
											}
											else if (pos == 1)
											{
												final EditText edittext=new EditText(MainActivity.this);
												ShowEditDialog("Start from...","Enter address to start analyzing.",edittext,"OK",new DialogInterface.OnClickListener() {
														public void onClick(DialogInterface dialog3, int which)
														{
															instantEntry = parseAddress(edittext.getText().toString());
															DisassembleInstant();
														}			
													},"cancel",new DialogInterface.OnClickListener() {
														public void onClick(DialogInterface dialog4, int which)
														{
															dialog4.dismiss();
														}
													});
												//dialog2.dismiss();
											}
										}
									});
							}
							else if (pos == 1)
							{
								DisassembleFile();
							}
						}
					});
				break;
			case R.id.btnShowdetail:
				if (elfUtil == null)
				{
					AlertSelFile();
					return;
				}
				ShowDetail();
				break;
			case R.id.btnSaveDisasm:
				ExportDisasm();
				break;
			case R.id.btnSaveDetails:
				SaveDetail();
				break;
			case R.id.btAbort:
				if(workerThread!=null)
				{
					if(workerThread.isAlive())
					{
						workerThread.interrupt();
					}
				}
				break;
			default:
				break;
		}

	}

	private void ShowEditDialog(String title,String message,final EditText edittext,
								String positive,DialogInterface.OnClickListener pos,
								String negative,DialogInterface.OnClickListener neg)
	{
		android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MainActivity.this);
		builder.setTitle(title);
		builder.setMessage(message);
		builder.setView(edittext);
		builder.setPositiveButton(positive,pos);
		builder.setNegativeButton(negative,neg);
		builder.show();
		return ;
	}
	public static void ShowSelDialog(Activity a,final List<String> ListItems,String title,DialogInterface.OnClickListener listener)
	{
		final CharSequence[] items =  ListItems.toArray(new String[ ListItems.size()]);
		android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(a);
		builder.setTitle(title);
		builder.setItems(items,listener);
		builder.show();
	}

	private void ShowSelDialog(final List<String> ListItems,String title,DialogInterface.OnClickListener listener)
	{
		MainActivity.ShowSelDialog(this,ListItems,title,listener);
	}

	private long parseAddress(String toString)
	{
		if(toString==null)
		{
			return elfUtil.getEntryPoint();
		}
		if(toString.equals(""))
		{
			return elfUtil.getEntryPoint();
		}
		// TODO: Implement this method
		try{
			long l= Long.decode(toString);
			return l;
		}catch(NumberFormatException e)
		{
			Toast.makeText(this,"Did you enter valid address?",3).show();
		}
		return elfUtil.getEntryPoint();
	}

	private void AlertSelFile()
	{
		Toast.makeText(this, "Please Select a file first.", 2).show();
		showFileChooser();
	}

	private void ExportDisasm()
	{
		requestAppPermissions(this);
		if (fpath == null || "".compareToIgnoreCase(fpath) == 0)
		{
			AlertSelFile();
			return;
		}
		if(currentProject==null)
		{
			final EditText etName=new EditText(this);
			ShowEditDialog("Create new Project", "Enter new project name", etName, "OK", new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface p1,int  p2)
					{
						// TODO: Implement this method
						String projn=etName.getText().toString();
						SaveDisasmNewProject(projn);
						return ;
					}
				}, "Cancel", new DialogInterface.OnClickListener(){

					@Override
					public void onClick(DialogInterface p1, int p2)
					{		
						return ;
					}				
				});
		}else{
			ShowExportOptions();
		}
			
	}

	private void ExportDisasmSub(int mode)
	{
		Log.v(TAG, "Saving disassembly");
		if(mode==0)//Raw mode
		{
			SaveDisasmRaw();
			return;
		}
		if(mode==4)//Database mode
		{
			SaveDisasm(currentProject.getDisasmDb());
			return;
		}
		File dir=new File(projectManager.RootFile,currentProject.name+"/");
		Log.d(TAG,"dirpath="+dir.getAbsolutePath());
		File file=new File(dir, "Disassembly_" + new Date(System.currentTimeMillis()).toString() + (mode==3 ? ".json":".txt"));
		Log.d(TAG,"filepath="+file.getAbsolutePath());
		dir.mkdirs();
		try
		{
			file.createNewFile();
		}
		catch (IOException e)
		{
			Log.e(TAG, "", e);
			Toast.makeText(this, "Something went wrong saving file", 3).show();
		}
		//Editable et=etDetails.getText();
		try
		{
			FileOutputStream fos=new FileOutputStream(file);
			try
			{
				StringBuilder sb=new StringBuilder();
				ArrayList<ListViewItem> items=adapter.itemList();
				for (ListViewItem lvi:items)
				{
					switch (mode)
					{
						case 1:
							sb.append(lvi.address);
							sb.append("\t");
							sb.append(lvi.bytes);
							sb.append("\t");
							sb.append(lvi.instruction);
							sb.append(" ");
							sb.append(lvi.operands);
							sb.append("\t");
							sb.append(lvi.comments);
							break;
						case 2:
							sb.append(lvi.address);
							sb.append(":");
							sb.append(lvi.instruction);
							sb.append(" ");
							sb.append(lvi.operands);
							sb.append("  ;");
							sb.append(lvi.comments);
							break;
						case 3:
							sb.append(lvi.toString());
					}	
					sb.append(System.lineSeparator());
				}
				fos.write(sb.toString().getBytes());
			}
			catch (IOException e)
			{
				AlertError( "", e);
				return;
			}
		}
		catch (FileNotFoundException e)
		{
			AlertError("", e);
		}
		AlertSaveSuccess(file);
	}

	private void SaveDisasmRaw()
	{
		File dir=new File(projectManager.RootFile,currentProject.name+"/");
		Log.d(TAG,"dirpath="+dir.getAbsolutePath());
		File file=new File(dir, "Disassembly.raw");
		Log.d(TAG,"filepath="+file.getAbsolutePath());
		dir.mkdirs();
		try
		{
			file.createNewFile();
		}
		catch (IOException e)
		{
			Log.e(TAG, "", e);
			Toast.makeText(this, "Something went wrong saving file", 3).show();
		}
		try
		{
			FileOutputStream fos = new FileOutputStream(file);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(disasmResults);
			oos.close();
		}
		catch (IOException e)
		{
			AlertError("Failed to save disasm as a Raw file",e);
			return;
		}
		AlertSaveSuccess(file);
		return ;
	}
	
	private void SaveDetail()
	{
		requestAppPermissions(this);
		if (fpath == null || "".compareToIgnoreCase(fpath) == 0)
		{
			AlertSelFile();
			return;
		}
		if(currentProject==null)
		{
			final EditText etName=new EditText(this);
			ShowEditDialog("Create new Project", "Enter new project name", etName, "OK", new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface p1,int  p2)
					{
						// TODO: Implement this method
						String projn=etName.getText().toString();
						SaveDetailNewProject(projn);
						return ;
					}
				}, "Cancel", new DialogInterface.OnClickListener(){

					@Override
					public void onClick(DialogInterface p1, int p2)
					{
						// TODO: Implement this method
						return ;
					}				
				});
		}else{
			try
			{
				SaveDetailSub(currentProject);
			}
			catch (IOException e)
			{
				AlertError("Error saving details",e);
			}
		}

		//SaveDetailOld();
	}

	private void SaveDetailNewProject(String projn)
	{
		// TODO: Implement this method
		try
		{
			ProjectManager.Project proj=projectManager.newProject(projn, fpath);
			proj.Open(false);
			db=new DatabaseHelper(this,ProjectManager.createPath(proj.name)+"disasm.db");
			SaveDetailSub(proj);
		}
		catch (IOException e)
		{
			AlertError("Error creating a project!!",e);
		}
		return ;
	}

	private void SaveDetailSub(ProjectManager.Project proj) throws IOException
	{
		File detailF=proj.getDetailFile();
		if (detailF == null)
			throw new IOException("Failed to create detail File");
		currentProject = proj;
		detailF.createNewFile();
		SaveDetail(new File(ProjectManager.Path), detailF);
		proj.Save();
	}
	private void SaveDisasmNewProject(String projn)
	{	
		try
		{
			ProjectManager.Project proj=projectManager.newProject(projn, fpath);
			currentProject=proj;
			proj.Open(false);
			db=new DatabaseHelper(this,ProjectManager.createPath(proj.name)+"disasm.db");
			ShowExportOptions();
			proj.Save();
			
		}
		catch (IOException e)
		{
			AlertError("Error creating a project!!",e);
		}
		return ;
	}

	private void ShowExportOptions()
	{
		final List<String> ListItems = new ArrayList<>();
		ListItems.add("Raw(Fast,Reloadable)");
        ListItems.add("Classic(Addr bytes inst op comment)");
        ListItems.add("Simple(Addr: inst op; comment");
        ListItems.add("Json");
		ListItems.add("Database(.db, reloadable)");
		ShowSelDialog(this, ListItems, "Export as...", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int pos)
				{
					//String selectedText = items[pos].toString();
					dialog.dismiss();
					final ProgressDialog dialog2= showProgressDialog("Saving...");
					ExportDisasmSub(pos);
					dialog2.dismiss();
				}
			});
	}
	class SaveDBAsync extends AsyncTask<DatabaseHelper, Integer, Void>
	{
		String TAG = getClass().getSimpleName();
		android.app.AlertDialog.Builder builder;
		ProgressBar progress;
		protected void onPreExecute (){
			super.onPreExecute();
			Log.d(TAG + " PreExceute","On pre Exceute......");
			progress=new ProgressBar(MainActivity.this);
			progress.setIndeterminate(false);
			
			builder=new android.app.AlertDialog.Builder(MainActivity.this);
			builder.setTitle("Saving..").setView(progress);
			builder.show();
		}

		protected Void doInBackground(DatabaseHelper...disasmF) {
			Log.d(TAG + " DoINBackGround","On doInBackground...");

			int cnt=disasmF[0].getCount();
			if(cnt==0)
			{
				int datasize=disasmResults.size();
				for(int i=0;i<datasize;++i)
				{
					disasmF[0].insert(disasmResults.get(i));
					publishProgress(i);
				}
			}
			return null;
		}

		protected void onProgressUpdate(Integer...a){
			super.onProgressUpdate(a);
			progress.setProgress(a[0]);
			//Log.d(TAG + " onProgressUpdate", "You are in progress update ... " + a[0]);
		}
/*
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			//Log.d(TAG + " onPostExecute", "" + result);
		}
		*/
	}
	private void  SaveDisasm(DatabaseHelper disasmF)
	{
		// TODO: Implement this method
		new SaveDBAsync().execute(disasmF);
		return ;
	}
	
	private void AlertError(String p0, Exception e)
	{
		ShowErrorDialog(this,p0,e);
		//ShowAlertDialog((Activity)this,p0,Log.getStackTraceString(e));
		Log.e(TAG,p0,e);
		return ;
	}
	
	private void SaveDetailOld()
	{
		Log.v(TAG, "Saving details");
		File dir=new File("/sdcard/disasm/");
		File file=new File(dir, new File(fpath).getName() + "_" + new Date(System.currentTimeMillis()).toString() + ".details.txt");
		SaveDetail(dir, file);
	}

	private void SaveDetail(File dir, File file)
	{
		dir.mkdirs();
		try
		{
			file.createNewFile();
		}
		catch (IOException e)
		{
			Log.e(TAG, "", e);
			Toast.makeText(this, "Something went wrong saving file", 3).show();
		}
	
		try
		{
			FileOutputStream fos=new FileOutputStream(file);
			try
			{
				fos.write(elfUtil.toString().getBytes());
			}
			catch (IOException e)
			{
				Log.e(TAG, "", e);
			}
		}
		catch (FileNotFoundException e)
		{
			Log.e(TAG, "", e);
		}

		AlertSaveSuccess(file);
	}

	private void AlertSaveSuccess(File file)
	{
		Toast.makeText(this, "Successfully saved to file: " + file.getPath(), 5).show();
	}

	private void ShowDetail()
	{
		etDetails.setText(elfUtil.toString());
	}

	private void DisassembleInstant()
	{
		Toast.makeText(this,"Not supported by now. Please just use persist mode instead.",3).show();

		long startaddress=instantEntry;//file offset
		long index=startaddress;
		long addr=elfUtil.getCodeSectionVirtAddr();
		long limit=startaddress + 400;
		if(limit>=filecontent.length)
		{
			Toast.makeText(this,"Odd address :(",3).show();
			return;
		}
		btDisasm.setEnabled(false);
		//disasmResults.clear();
		//setupListView();
		/*for (;;)
		 {
		 /*DisasmResult dar=new DisasmResult(filecontent, index, addr);
		 if (dar.size == 0)
		 {
		 dar.size = 4;
		 dar.mnemonic = "db";
		 dar.bytes = new byte[]{filecontent[(int)index],filecontent[(int)index + 1],filecontent[(int)index + 2],filecontent[(int)index + 3]};
		 dar.op_str = "";
		 Log.e(TAG, "Dar.size==0, breaking?");
		 //break;
		 }
		 final ListViewItem lvi=new ListViewItem(dar);
		 //disasmResults.add(lvi);
		 adapter.addItem(lvi);
		 adapter.notifyDataSetChanged();
		 Log.v(TAG, "i=" + index + "lvi=" + lvi.toString());
		 if (index >= limit)
		 {
		 Log.i(TAG, "index is " + index + ", breaking");
		 break;
		 }
		 Log.v(TAG, "dar.size is =" + dar.size);
		 Log.i(TAG, "" + index + " out of " + (limit - startaddress));
		 /*if((limit-start)%320==0){
		 mBuilder.setProgress((int)(limit-startaddress), (int)(index-start), false);
		 // Displays the progress bar for the first time.
		 mNotifyManager.notify(0, mBuilder.build());
		 }//
		 index += dar.size;
		 addr += dar.size;

		 }*/
		//Currently not suported

		btDisasm.setEnabled(true);
	}

	public final Runnable runnableRequestLayout=new Runnable(){
		@Override
		public void run()
		{
			//adapter.notifyDataSetChanged();
			listview.requestLayout();
		}
	};

//	final Runnable runnableAddItem=new Runnable(){
//		@Override
//		public void run()
//		{
//			adapter.addItem(lvi);
//			adapter.notifyDataSetChanged();
//			return ;
//		}
//	};
	ListViewItem lvi;
	//TODO: DisassembleFile(long address, int amt);
	private void DisassembleFile()
	{
		Toast.makeText(this, "started", 2).show();
		Log.v(TAG, "Strted disassm");
		btDisasm.setEnabled(false);
		btAbort.setEnabled(true);
		btSavDisasm.setEnabled(false);
		//final ProgressDialog dialog= showProgressDialog("Disassembling...");
		disasmResults.clear();
		setupListView();
		mNotifyManager =(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mBuilder = new Notification.Builder(this);
		mBuilder.setContentTitle("Disassembler")
			.setContentText("Disassembling in progress")
			.setSmallIcon(R.drawable.cell_shape)
			.setOngoing(true)
			.setProgress(100, 0, false);
		/*Intent snoozeIntent = new Intent(this, MyBroadcastReceiver.class);
		 snoozeIntent.setAction(ACTION_SNOOZE);
		 snoozeIntent.putExtra(EXTRA_NOTIFICATION_ID, 0);
		 PendingIntent snoozePendingIntent =
		 PendingIntent.getBroadcast(this, 0, snoozeIntent, 0);
		 mBuilder.addAction(R.drawable.ic_launcher,"",snoozeIntent);*/
		workerThread = new Thread(new Runnable(){
				@Override
				public void run()
				{
					long start=elfUtil.getCodeSectionOffset();
					long index=start;
					long limit=elfUtil.getCodeSectionLimit();
					long addr=elfUtil.getCodeSectionVirtAddr();
					Log.v(TAG, "code section point :" + Long.toHexString(index));
					//ListViewItem lvi;
					//	getFunctionNames();
					long size=limit - start;
					long leftbytes=size;
					DisasmIterator dai=new DisasmIterator(MainActivity.this,mNotifyManager,mBuilder,adapter,size);
					dai.getAll(filecontent,start,size,addr, disasmResults);	
					mNotifyManager.cancel(0);
					final int len=disasmResults.size();
					//add xrefs

					runOnUiThread(new Runnable(){
							@Override
							public void run()
							{
								listview.requestLayout();
								tab2.invalidate();
								//dialog.dismiss();
								btDisasm.setEnabled(true);
								btAbort.setEnabled(false);
								btSavDisasm.setEnabled(true);
								Toast.makeText(MainActivity.this, "done", 2).show();			
							}
						});
					Log.v(TAG, "disassembly done");		
				}});
		workerThread.start();
	}
	View.OnClickListener rowClkListener= new OnClickListener() {
		public void onClick(View view)
		{
			TableRow tablerow = (TableRow) view; 
			ListViewItem lvi= (ListViewItem) tablerow.getTag();
			//TextView sample = (TextView) tablerow.getChildAt(1);
			tablerow.setBackgroundColor(Color.GREEN);	
		}
	};
	private void SendErrorReport(Throwable error)
	{
		final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);

		emailIntent.setType("plain/text");

		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL,
							 new String[] { "1641832e@fire.fundersclub.com" });

		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
							 "Crash report");
		StringBuilder content=new StringBuilder(Log.getStackTraceString(error));
		
		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT,
							 content.toString());

		startActivity(Intent.createChooser(emailIntent, "Send crash report as an issue by email"));
	}

	public void AdjustShow(TextView t1v, TextView t2v, TextView t3v, TextView t4v, TextView t5v, TextView t6v, TextView t7v)
	{
		t1v.setVisibility(isShowAddress() ? View.VISIBLE: View.GONE);
		t2v.setVisibility(isShowLabel() ? View.VISIBLE: View.GONE);
		t3v.setVisibility(isShowBytes() ? View.VISIBLE: View.GONE);
		t4v.setVisibility(isShowInstruction() ? View.VISIBLE: View.GONE);
		t5v.setVisibility(isShowCondition() ? View.VISIBLE: View.GONE);
		t6v.setVisibility(isShowOperands() ? View.VISIBLE: View.GONE);
		t7v.setVisibility(isShowComment() ? View.VISIBLE: View.GONE);
	}

	public static final int REQUEST_WRITE_STORAGE_REQUEST_CODE=1;
	public static void requestAppPermissions(Activity a)
	{
		if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
		{
			return;
		}
		if (hasReadPermissions(a) && hasWritePermissions(a)/*&&hasGetAccountPermissions(a)*/)
		{
			Log.i(TAG, "Has permissions");
			return;
		}
		showPermissionRationales(a);
		a.requestPermissions(new String[] {
								 Manifest.permission.READ_EXTERNAL_STORAGE,
								 Manifest.permission.WRITE_EXTERNAL_STORAGE
								 //,Manifest.permission.GET_ACCOUNTS
							 }, REQUEST_WRITE_STORAGE_REQUEST_CODE); // your request code
	}

	private static boolean  hasGetAccountPermissions(Context c)
	{
		// TODO: Implement this method
		return c.checkSelfPermission(Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED;
	}

	public static boolean hasReadPermissions(Context c)
	{
		return c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
	}

	public static boolean hasWritePermissions(Context c)
	{
		return c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
	}
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		//final Thread.UncaughtExceptionHandler ori=Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){
				@Override
				public void uncaughtException(Thread p1, Throwable p2)
				{
					// TODO: Implement this method
					Toast.makeText(MainActivity.this,Log.getStackTraceString(p2),3).show();
					if(p2 instanceof SecurityException)
					{
						Toast.makeText(MainActivity.this,"Did you grant required permissions to this app?",3).show();
						setting=getSharedPreferences(RATIONALSETTING,MODE_PRIVATE);
						editor=setting.edit();
						editor.putBoolean("show",true);
						editor.commit();
					}
					requestAppPermissions(MainActivity.this);
					//String [] accs=getAccounts();
					SendErrorReport(p2);
					//	ori.uncaughtException(p1, p2);
					Log.wtf(TAG,"UncaughtException",p2);
					finish();
					return ;
				}

				
				private String[] getAccounts() {
					Pattern emailPattern = Patterns.EMAIL_ADDRESS;
					Account[] accounts = AccountManager.get(MainActivity.this).getAccounts();
					if(accounts==null)
					{
						return new String[]{""};
					}
					ArrayList<String> accs=new ArrayList<>();
					for (Account account : accounts) {
						if (emailPattern.matcher(account.name).matches()) {
							String email = account.name;
							accs.add(email);
							//Log.d(TAG, "email : " + email);
						}
					}
					return accs.toArray(new String[accs.size()]);
				}
			});
		try
		{
			if(Init()==-1)
			{
				throw new RuntimeException();
			}
			//cs = new Capstone(Capstone.CS_ARCH_ARM, Capstone.CS_MODE_ARM);
			//cs.setDetail(Capstone.CS_OPT_ON);
		}
		catch (RuntimeException e)
		{
			Toast.makeText(this, "Failed to initialize the native engine: " + Log.getStackTraceString(e), 10).show();
			android.os.Process.killProcess(android.os.Process.getGidForName(null));
		}
        setContentView(R.layout.main);
		etDetails = (EditText) findViewById(R.id.detailText);
		Button selectFile=(Button) findViewById(R.id.selFile);
		selectFile.setOnClickListener(this);
		btShowDetails = (Button) findViewById(R.id.btnShowdetail);
		btShowDetails.setOnClickListener(this);
		btDisasm = (Button) findViewById(R.id.btnDisasm);
		btDisasm.setOnClickListener(this);
		btSavDisasm = (Button) findViewById(R.id.btnSaveDisasm);
		btSavDisasm.setOnClickListener(this);
		btSavDit = (Button) findViewById(R.id.btnSaveDetails);
		btSavDit.setOnClickListener(this);
		btAbort = (Button) findViewById(R.id.btAbort);

		btAbort.setOnClickListener(this);
		btAbort.setEnabled(false);

		etFilename = (EditText) findViewById(R.id.fileNameText);
		etFilename.setFocusable(false);
		etFilename.setEnabled(false);

		lvSymbols=(ListView)findViewById(R.id.symlistView);
		symbolLvAdapter=new SymbolListAdapter();
		lvSymbols.setAdapter(symbolLvAdapter);
		//symAdapter = new SymbolTableAdapter(this.getApplicationContext());
		//tvSymbols = (TableView)findViewById(R.id.content_container);
		//tvSymbols.setAdapter(symAdapter);
		
		tabHost = (TabHost) findViewById(R.id.tabhost1);
        tabHost.setup();
		TabHost.TabSpec tab0 = tabHost.newTabSpec("1").setContent(R.id.tab0).setIndicator("Overview");
        TabHost.TabSpec tab1 = tabHost.newTabSpec("2").setContent(R.id.tab1).setIndicator("Details");
        TabHost.TabSpec tab2 = tabHost.newTabSpec("3").setContent(R.id.tab2).setIndicator("Disassembly");
		TabHost.TabSpec tab3 = tabHost.newTabSpec("4").setContent(R.id.tab3).setIndicator("Symbols");
		tabHost.addTab(tab0);
        tabHost.addTab(tab1);
		tabHost.addTab(tab3);
        tabHost.addTab(tab2);
		
		this.tab1 = (LinearLayout) findViewById(R.id.tab1);
		this.tab2 = (LinearLayout) findViewById(R.id.tab2);
	
		/*if (cs == null)
		 {
		 Toast.makeText(this, "Failed to initialize the native engine", 3).show();
		 android.os.Process.killProcess(android.os.Process.getGidForName(null));
		 }*/
		//tlDisasmTable = (TableLayout) findViewById(R.id.table_main);
		//	TableRow tbrow0 = new TableRow(MainActivity.this);
		//	CreateDisasmTopRow(tbrow0);		
		//	tlDisasmTable.addView(tbrow0);
		setupListView();

		setting = getSharedPreferences(RATIONALSETTING, MODE_PRIVATE);
		boolean show=setting.getBoolean("show",true);
		if(show){
			showPermissionRationales();
			editor=setting.edit();
			editor.putBoolean("show",false);
			editor.commit();
		}
		requestAppPermissions(this);
		mProjNames = new String[]{"Exception","happened"};
		try
		{
			projectManager = new ProjectManager(this);
			mProjNames=projectManager.strProjects();//new String[]{"a","v","vf","vv"}; //getResources().getStringArray(R.array.planets_array);		
		}
		catch (IOException e)
		{
			AlertError("Failed to load projects",e);
		}
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        // Set the adapter for the list view
        mDrawerList.setAdapter(new ArrayAdapter<String>(this,
														R.layout.row, mProjNames));
        // Set the list's click listener
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

		//https://www.androidpub.com/1351553
		Intent intent = getIntent();
		if (intent.getAction().equals(Intent.ACTION_VIEW)) {
			// User opened this app from file browser
			String filePath = intent.getData().getPath();
			String[] toks=filePath.split(".");
			int last=toks.length-1;
			String ext="";
			if(last>=0){
				ext=toks[last];
				if("adp".equalsIgnoreCase(ext))
				{
					//User opened the project file
					projectManager.Open(toks[last-1]);
					
				}
			}else{
			//User opened pther files
				OnChoosePath(intent.getData());
			}
		} else { // android.intent.action.MAIN	
			String lastProj=setting.getString(LASTPROJKEY, "");
			if(projectManager!=null)
				projectManager.Open(lastProj);
		}
	}

	private class DrawerItemClickListener implements ListView.OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			//selectItem(position);
			if(view instanceof TextView)
			{
				TextView tv=(TextView)view;
				String projname=tv.getText().toString();
				projectManager.Open(projname);
			}
		}
	}

	/** Swaps fragments in the main content view */
	private void selectItem(int position) {
		//Project project=
		// Create a new fragment and specify the planet to show based on position
		/*Fragment fragment = new PlanetFragment();
		 Bundle args = new Bundle();
		 args.putInt(PlanetFragment.ARG_PLANET_NUMBER, position);
		 fragment.setArguments(args);

		 // Insert the fragment by replacing any existing fragment
		 FragmentManager fragmentManager = getFragmentManager();
		 fragmentManager.beginTransaction()
		 .replace(R.id.content_frame, fragment)
		 .commit();

		 // Highlight the selected item, update the title, and close the drawer
		 mDrawerList.setItemChecked(position, true);
		 setTitle(mPlanetTitles[position]);
		 mDrawerLayout.closeDrawer(mDrawerList);*/
	}

	@Override
	public void setTitle(CharSequence title) {
		//mTitle = title;
		//getActionBar().setTitle(mTitle);
	}

	private void showPermissionRationales()
	{
		showPermissionRationales(this);
	}
	public static void showPermissionRationales(Activity a)
	{
		ShowAlertDialog(a,"Permissions","- Read/Write storage(obvious)\r\n- GetAccounts: add email address info on crash report.\r\n\r\n For more information visit https://github.com/KYHSGeekCode/Android-Disassembler/");
	}
	private void ShowErrorDialog(Activity a,String title,final Throwable err)
	{
		android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(a);
		builder.setTitle(title);
		builder.setCancelable(false);
		builder.setMessage(Log.getStackTraceString(err));
		builder.setPositiveButton("OK", (DialogInterface.OnClickListener)null);
		builder.setNegativeButton("Send error report", new DialogInterface.OnClickListener(){
				@Override
				public void onClick(DialogInterface p1,int  p2)
				{
					// TODO: Implement this method
					SendErrorReport(err);
					return ;
				}
			});
		builder.show();
	}
	
	private static void ShowAlertDialog(Activity a,String title,String content)
	{
		android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(a);
		builder.setTitle(title);
		builder.setCancelable(false);
		builder.setMessage(content);
		builder.setPositiveButton("OK", (DialogInterface.OnClickListener)null);
		builder.show();
	}

	private void setupListView()
	{
		adapter = new ListViewAdapter();
		listview = (ListView) findViewById(R.id.listview);
        listview.setAdapter(adapter);
		listview.setOnItemClickListener(new DisasmClickListener());

	}
	public static int getScreenHeight()
	{
		return Resources.getSystem().getDisplayMetrics().heightPixels;
	}
	private void CreateDisasmTopRow(TableRow tbrow0)
	{
		TextView tv0 = new TextView(MainActivity.this);
		tv0.setText(" Address ");
		tv0.setTextColor(Color.BLACK);
		tbrow0.addView(tv0);
		TextView tv1 = new TextView(MainActivity.this);
		tv1.setText(" Label ");
		tv1.setTextColor(Color.BLACK);
		tbrow0.addView(tv1);
		TextView tv2 = new TextView(MainActivity.this);
		tv2.setText(" Bytes ");
		tv2.setTextColor(Color.BLACK);
		tbrow0.addView(tv2);
		TextView tv3 = new TextView(MainActivity.this);
		tv3.setText(" Inst ");
		tv3.setTextColor(Color.BLACK);
		tbrow0.addView(tv3);
		TextView tv4 = new TextView(MainActivity.this);
		tv4.setText(" Cond ");
		tv4.setTextColor(Color.BLACK);
		tbrow0.addView(tv4);
		TextView tv5 = new TextView(MainActivity.this);
		tv5.setText(" Operands ");
		tv5.setTextColor(Color.BLACK);
		tbrow0.addView(tv5);
		TextView tv6 = new TextView(MainActivity.this);
		tv6.setText(" Comment ");
		tv6.setTextColor(Color.BLACK);
		AdjustShow(tv0, tv1, tv2, tv3, tv4, tv5, tv6);
		tbrow0.addView(tv6);
	}
	public void RefreshTable()
	{
		//tlDisasmTable.removeAllViews();
		//TableRow tbrow0 = new TableRow(MainActivity.this);
		//CreateDisasmTopRow(tbrow0);		
		//tlDisasmTable.addView(tbrow0);
		//for(int i=0;i<disasmResults.size();++i)
		{
			//AddOneRow(disasmResults.get(i));
		}
		//tlDisasmTable.refreshDrawableState();
	}

	@Override
	protected void onDestroy()
	{
		// TODO: Implement this method
		super.onDestroy();
		try
		{
			elfUtil.close();
		}
		catch (Exception e)
		{}
		Finalize();
		if (cs != null)
			cs.close();
		cs = (Capstone) null;
		//Finalize();
		if (mNotifyManager != null)
		{
			mNotifyManager.cancel(0);
			mNotifyManager.cancelAll();
		}
		//maybe service needed.
		/*if(workerThread!=null)
		 {
		 workerThread.stop();
		 }*/
	}
	@Override
    public boolean onCreateOptionsMenu(Menu menu)
	{
        // Inflate the menu; this adds items to the action bar if it is present.
        // 메뉴버튼이 처음 눌러졌을 때 실행되는 콜백메서드
        // 메뉴버튼을 눌렀을 때 보여줄 menu 에 대해서 정의
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
	@Override
    public boolean onPrepareOptionsMenu(Menu menu)
	{
        Log.d("test", "onPrepareOptionsMenu - 옵션메뉴가 " +
			  "화면에 보여질때 마다 호출됨");
		/* // 로그인 한 상태: 로그인은 안보이게, 로그아웃은 보이게
		 menu.getItem(0).setEnabled(true);
		 }else{ // 로그 아웃 한 상태 : 로그인 보이게, 로그아웃은 안보이게
		 menu.getItem(0).setEnabled(false);
		 menu.getItem(1).setEnabled(true);
		 */
        return super.onPrepareOptionsMenu(menu);
    }
	@Override
    public boolean onOptionsItemSelected(MenuItem item)
	{
        // 메뉴의 항목을 선택(클릭)했을 때 호출되는 콜백메서드
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        Log.d("test", "onOptionsItemSelected - 메뉴항목을 클릭했을 때 호출됨");
        int id = item.getItemId();
		switch (id)
		{
			case R.id.settings: {
					Intent SettingActivity = new Intent(this, SettingsActivity.class);
					startActivity(SettingActivity);
				}
				break;
			case R.id.rows:
				{
					mCustomDialog = new CustomDialog(this, 
													 "Select rows to view", // 제목
													 "Choose rows(Nothing happens)", // 내용 
													 (View.OnClickListener)null, // 왼쪽 버튼 이벤트
													 rightListener); // 오른쪽 버튼 이벤트
					mCustomDialog.show();
					break;
				}
		}
        return super.onOptionsItemSelected(item);
    }
	private View.OnClickListener leftListener = new View.OnClickListener() {
		public void onClick(View v)
		{
			Toast.makeText(getApplicationContext(), "왼쪽버튼 클릭",
						   Toast.LENGTH_SHORT).show();
			mCustomDialog.dismiss();
		}
	};

	private View.OnClickListener rightListener = new View.OnClickListener() {
		public void onClick(View v)
		{
			Toast.makeText(getApplicationContext(), "오른쪽버튼 클릭",
						   Toast.LENGTH_SHORT).show();
		}
	};

	//private static final int FILE_SELECT_CODE = 0;

	private void showFileChooser()
	{
		requestAppPermissions(this);
		//SharedPreferences sharedPreferences = null;
		settingPath=getSharedPreferences("path",MODE_PRIVATE);
		String prepath=settingPath.getString(DiskUtil.SC_PREFERENCE_KEY,"/storage/emulated/0/");
		File tmp=new File(prepath);
		if(tmp.isFile())
		{
			tmp=tmp.getParentFile();
			prepath=tmp.getAbsolutePath();
		}
		StorageChooser chooser = new StorageChooser.Builder()
			.withActivity(MainActivity.this)
			.withFragmentManager(getFragmentManager())
			.withMemoryBar(true)
			.allowCustomPath(true)
			.setType(StorageChooser.FILE_PICKER)
			.actionSave(true)
			//.withPreference(settingPath)
			.withPredefinedPath(prepath)
			.build();
// Show dialog whenever you want by
		chooser.show();
// get path that the user has chosen
		chooser.setOnSelectListener(new StorageChooser.OnSelectListener() {
				@Override
				public void onSelect(String path) {
					SharedPreferences.Editor edi=settingPath.edit();
					edi.putString(DiskUtil.SC_PREFERENCE_KEY,path);
					edi.commit();
					OnChoosePath(path);
					//Log.e("SELECTED_PATH", path);
				}
			});
		//Intent i=new Intent(this, FileSelectorActivity.class);
		//startActivityForResult(i, REQUEST_SELECT_FILE);		
		/*
		 Intent intent = new Intent();
		 intent.setAction(Intent.ACTION_GET_CONTENT);
		 //아래와 같이 할 경우 mime-type에 해당하는 파일만 선택 가능해집니다.
		 intent.setType("application/*");
		 intent.addCategory(Intent.CATEGORY_OPENABLE);
		 try
		 {
		 startActivityForResult(
		 Intent.createChooser(intent, "Select a File"),
		 FILE_SELECT_CODE);
		 }
		 catch (android.content.ActivityNotFoundException ex)
		 {
		 // Potentially direct the user to the Market with a Dialog
		 Toast.makeText(this, "Please install a File Manager.",
		 Toast.LENGTH_SHORT).show();
		 }*/
	}
	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults)
	{
		switch (requestCode)
		{
			case REQUEST_WRITE_STORAGE_REQUEST_CODE: {
					// If request is cancelled, the result arrays are empty.
					if (grantResults.length > 0
						&& grantResults[0] == PackageManager.PERMISSION_GRANTED)
					{

						// permission was granted, yay! Do the
						// contacts-related task you need to do.

					}
					else
					{
						setting=getSharedPreferences(RATIONALSETTING,MODE_PRIVATE);
						editor=setting.edit();
						editor.putBoolean("show",true);
						editor.commit();
						// permission denied, boo! Disable the
						// functionality that depends on this permission.
					}
					return;
				}

				// other 'case' lines to check for other
				// permissions this app might request
		}
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
				/*case FILE_SELECT_CODE:
				 if (resultCode == RESULT_OK)
				 {
				 // Get the Uri of the selected file
				 Uri uri = data.getData();
				 //File file=new File(uri.);
				 //URI -> real file path
				 try
				 {
				 String file_path;
				 if (new File(uri.getPath()).exists() == false)
				 {
				 file_path = RealPathUtils.getRealPathFromURI(this, uri);
				 }
				 else
				 {
				 file_path = uri.getPath();
				 }	
				 etFilename.setText(file_path);
				 fpath = file_path; //uri.getPath();
				 File file=new File(file_path);
				 long fsize=file.length();
				 int index=0;
				 filecontent = new byte[(int)fsize];
				 DataInputStream in = new DataInputStream(new FileInputStream(fpath));
				 int len,counter=0;
				 byte[] b=new byte[1024];
				 while ((len = in.read(b)) > 0)
				 {
				 for (int i = 0; i < len; i++)
				 { // byte[] 버퍼 내용 출력
				 //System.out.format("%02X ", b[i]);
				 filecontent[index] = b[i];
				 index++;
				 counter++;
				 }
				 }
				 elfUtil = new ELFUtil(file, filecontent);
				 Toast.makeText(this, "success size=" + new Integer(index).toString(), 1).show();
				 }
				 catch (Exception e)
				 {
				 Toast.makeText(this, Log.getStackTraceString(e), 30).show();
				 Log.e(TAG, "Nooo", e);
				 } 	
				 }
				 break;
				 */
			case REQUEST_SELECT_FILE:
				if (resultCode == Activity.RESULT_OK)
				{
					//OnChoosePath(data);
				}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	private void OnChoosePath(Uri uri)
	{
		try
		{
			InputStream is=(InputStream)getContentResolver().openInputStream(uri);
			//ByteArrayOutputStream bis=new ByteArrayOutputStream();
			filecontent=Utils.getBytes(is);
			//filecontent=is.toString().getBytes();
			/*filecontent=new byte[1024];
			while(is.read(filecontent,0,1024)>0)
			{
				
			}
			is.read(filecontent);
			is.close();*/
			File tmpfile=new File(getExternalFilesDir("directopen"),"tmp.so");
			tmpfile.createNewFile();
			FileOutputStream fos=new FileOutputStream(tmpfile);
			fos.write(filecontent);
			//elfUtil=new ELFUtil(new FileChannel().transferFrom(Channels.newChannel(is),0,0),filecontent);
			elfUtil=new ELFUtil(tmpfile,filecontent);
			fpath=tmpfile.getAbsolutePath();//uri.getPath();
			AfterReadFully();
			
		}
		catch (IOException e)
		{
			AlertError("Failed to read file",e);
		}
	}
	/*
	 * @(#)ASCIIUtility.java  1.10 05/08/29
	 *
	 * Copyright 1997-2005 Sun Microsystems, Inc. All Rights Reserved.
	 */

	public static class Utils {

		public static byte[] getBytes(InputStream is) throws IOException {

			int len;
			int size = 1024;
			byte[] buf;

			if (is instanceof ByteArrayInputStream) {
				size = is.available();
				buf = new byte[size];
				len = is.read(buf, 0, size);
			} else {
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				buf = new byte[size];
				while ((len = is.read(buf, 0, size)) != -1)
					bos.write(buf, 0, len);
				buf = bos.toByteArray();
			}
			return buf;
		}


	}
	
	private void OnChoosePath(String p)//Intent data)
	{
		try
		{
			String path=p;//data.getStringExtra("com.jourhyang.disasmarm.path");
			File file=new File(path);
			fpath=path;
			etFilename.setText(file.getAbsolutePath());
			long fsize=file.length();
			int index=0;
			filecontent = new byte[(int)fsize];

			DataInputStream in = new DataInputStream(new FileInputStream(file));
			int len,counter=0;
			byte[] b=new byte[1024];
			while ((len = in.read(b)) > 0)
			{
				for (int i = 0; i < len; i++)
				{ // byte[] 버퍼 내용 출력
					//System.out.format("%02X ", b[i]);
					filecontent[index] = b[i];
					index++;
					counter++;
				}
			}
			in.close();
			elfUtil = new ELFUtil(file, filecontent);
			AfterReadFully();
			Toast.makeText(this, "success size=" + index /*+ type.name()*/, 3).show();
			
			//OnOpenStream(fsize, path, index, file);
		}catch (Exception e)
		{
			//Log.e(TAG, "", e);
			AlertError("Failed to open and parse the file",e);
			//Toast.makeText(this, Log.getStackTraceString(e), 30).show();
		}
	}

	private void AfterReadFully() throws IOException
	{
		List<ELFUtil.Symbol> list=elfUtil.getSymbols();
		for(int i=0;i<list.size();++i){
			symbolLvAdapter.addItem(list.get(i));
			symbolLvAdapter.notifyDataSetChanged();
		}
	//	symAdapter.setCellItems(list);
		try
		{
			MachineType type=elfUtil.elf.header.machineType;
			int arch=getArchitecture(type);
			if (arch == CS_ARCH_MAX || arch == CS_ARCH_ALL)
			{
				Toast.makeText(this, "Maybe I don't support this machine:" + type.name(), 3).show();
			}
			else
			{
				int err=0;
				if ((err = (new DisasmIterator(null, null, null, null, 0).CSoption(cs.CS_OPT_MODE, arch))) != cs.CS_ERR_OK)
				{
					Log.e(TAG, "setmode err" + err);
					Toast.makeText(this, "failed to set architecture" + err, 3).show();
				}
				//cs.setMode();
			}
		}
		catch (Exception e)
		{
			//not an elf file. try PE parser
			PE pe=PEParser.parse(fpath);
			if (pe != null)
			{
				PESignature ps =pe.getSignature();
				if (ps == null || !ps.isValid())
				{
					//What is it?
					Toast.makeText(this, "The file seems that it is neither a valid Elf file or PE file!", 3).show();
					throw new IOException(e);
				}
			}
			else
			{
				//What is it?
				Toast.makeText(this, "The file seems that it is neither a valid Elf file or PE file!", 3).show();
				throw new IOException(e);
			}
		}	
		//fpath = path;
	}
	private int getArchitecture(MachineType type)
	{
		// TODO: Implement this method
		switch(type)
		{
			case NONE://(0, "No machine"),
				return CS_ARCH_ALL;
			case M32://(1, "AT&T WE 32100"),
			case SPARC://(2, "SUN SPARC"),
				return CS_ARCH_SPARC;
			case i386: //(3, "Intel 80386"),
				return CS_ARCH_X86;
			case m68K: //(4, "Motorola m68k family"),
			case m88K: //(5, "Motorola m88k family"),
			case i860: //(7, "Intel 80860"),
				return CS_ARCH_X86;
			case MIPS: //(8, "MIPS R3000 big-endian"),
				return CS_ARCH_MIPS;
			case S370: //(9, "IBM System/370"),
			case MIPS_RS3_LE: //(10, "MIPS R3000 little-endian"),
				return CS_ARCH_MIPS;
			case PARISC: //(15, "HPPA"),
			case VPP500: //(17, "Fujitsu VPP500"),
			case SPARC32PLUS: //(18, "Sun's \"v8plus\""),
			case i960: //(19, "Intel 80960"),
				return CS_ARCH_X86;
			case PPC: //(20, "PowerPC"),
				return CS_ARCH_PPC;
			case PPC64: //(21, "PowerPC 64-bit"),
				return CS_ARCH_PPC;
			case S390: //(22, "IBM S390"),

			case V800: //(36, "NEC V800 series"),
			case FR20: //(37, "Fujitsu FR20"),
			case RH32: //(38, "TRW RH-32"),
			case RCE: //(39, "Motorola RCE"),
			case ARM: //(40, "ARM"),
				return CS_ARCH_ARM;
			case FAKE_ALPHA: //(41, "Digital Alpha"),
			case SH: //(42, "Hitachi SH"),
			case SPARCV9: //(43, "SPARC v9 64-bit"),
				return CS_ARCH_SPARC;
			case TRICORE: //(44, "Siemens Tricore"),
			case ARC: //(45, "Argonaut RISC Core"),
			case H8_300: //(46, "Hitachi H8/300"),
			case H8_300H: //(47, "Hitachi H8/300H"),
			case H8S: //(48, "Hitachi H8S"),
			case H8_500: //(49, "Hitachi H8/500"),
			case IA_64: //(50, "Intel Merced"),
				return CS_ARCH_X86;
			case MIPS_X: //(51, "Stanford MIPS-X"),
				return CS_ARCH_MIPS;
			case COLDFIRE: //(52, "Motorola Coldfire"),
			case m68HC12: //(53, "Motorola M68HC12"),
			case MMA: //(54, "Fujitsu MMA Multimedia Accelerator"),
			case PCP: //(55, "Siemens PCP"),
			case NCPU: //(56, "Sony nCPU embeeded RISC"),
			case NDR1: //(57, "Denso NDR1 microprocessor"),
			case STARCORE: //(58, "Motorola Start*Core processor"),
			case ME16: //(59, "Toyota ME16 processor"),
			case ST100: //(60, "STMicroelectronic ST100 processor"),
			case TINYJ: //(61, "Advanced Logic Corp. Tinyj emb.fam"),
			case x86_64: //(62, "x86-64"),
				return CS_ARCH_X86;
			case PDSP: //(63, "Sony DSP Processor"),

			case FX66: //(66, "Siemens FX66 microcontroller"),
			case ST9PLUS: //(67, "STMicroelectronics ST9+ 8/16 mc"),
			case ST7: //(68, "STmicroelectronics ST7 8 bit mc"),
			case m68HC16: //(69, "Motorola MC68HC16 microcontroller"),
			case m68HC11: //(70, "Motorola MC68HC11 microcontroller"),
			case m68HC08: //(71, "Motorola MC68HC08 microcontroller"),
			case m68HC05: //(72, "Motorola MC68HC05 microcontroller"),
			case SVX: //(73, "Silicon Graphics SVx"),
			case ST19: //(74, "STMicroelectronics ST19 8 bit mc"),
			case VAX: //(75, "Digital VAX"),
			case CRIS: //(76, "Axis Communications 32-bit embedded processor"),
			case JAVELIN: //(77, "Infineon Technologies 32-bit embedded processor"),
			case FIREPATH: //(78, "Element 14 64-bit DSP Processor"),
			case ZSP: //(79, "LSI Logic 16-bit DSP Processor"),
			case MMIX: //(80, "Donald Knuth's educational 64-bit processor"),
			case HUANY: //(81, "Harvard University machine-independent object files"),
			case PRISM: //(82, "SiTera Prism"),
			case AVR: //(83, "Atmel AVR 8-bit microcontroller"),
			case FR30: //(84, "Fujitsu FR30"),
			case D10V: //(85, "Mitsubishi D10V"),
			case D30V: //(86, "Mitsubishi D30V"),
			case V850: //(87, "NEC v850"),
			case M32R: //(88, "Mitsubishi M32R"),
			case MN10300: //(89, "Matsushita MN10300"),
			case MN10200: //(90, "Matsushita MN10200"),
			case PJ: //(91, "picoJava"),
			case OPENRISC: //(92, "OpenRISC 32-bit embedded processor"),
			case ARC_A5: //(93, "ARC Cores Tangent-A5"),
			case XTENSA: //(94, "Tensilica Xtensa Architecture"),
			case AARCH64: //(183, "ARM AARCH64"),
				return CS_ARCH_ARM64;
			case TILEPRO: //(188, "Tilera TILEPro"),
			case MICROBLAZE: //(189, "Xilinx MicroBlaze"),
			case TILEGX: //(191, "Tilera TILE-Gx");

		}
		Log.e(TAG,"Unsupported machine!!"+type.name());
		return CS_ARCH_ALL;
	}
	public static final int CS_ARCH_ARM = 0;
	public static final int CS_ARCH_ARM64 = 1;
	public static final int CS_ARCH_MIPS = 2;
	public static final int CS_ARCH_X86 = 3;
	public static final int CS_ARCH_PPC = 4;
	public static final int CS_ARCH_SPARC = 5;
	public static final int CS_ARCH_SYSZ = 6;
	public static final int CS_ARCH_XCORE = 7;
	public static final int CS_ARCH_MAX = 8;
	public static final int CS_ARCH_ALL = 0xFFFF; // query id for cs_support()

	private String getRealPathFromURI(Uri uri)
	{
		String filePath = "";
		filePath = uri.getPath();
		//경로에 /storage가 들어가면 real file path로 판단
		if (filePath.startsWith("/storage"))
			return filePath;
		String wholeID = DocumentsContract.getDocumentId(uri);
		//wholeID는 파일명이 abc.zip이라면 /document/B5D7-1CE9:abc.zip와 같습니다.
		// Split at colon, use second item in the array
		String id = wholeID.split(":")[0];
		//Log.e(TAG, "id = " + id);
		String[] column = { MediaStore.Files.FileColumns.DATA };
		//파일의 이름을 통해 where 조건식을 만듭니다.
		String sel = MediaStore.Files.FileColumns.DATA + " LIKE '%" + id + "%'";
		//External storage에 있는 파일의 DB를 접근하는 방법 입니다.
		Cursor cursor = getContentResolver().query(MediaStore.Files.getContentUri("external"), column, sel, null, null);
		//SQL문으로 표현하면 아래와 같이 되겠죠????
		//SELECT _dtat FROM files WHERE _data LIKE '%selected file name%'
		int columnIndex = cursor.getColumnIndex(column[0]);
		if (cursor.moveToFirst())
		{
			filePath = cursor.getString(columnIndex);
		}
		cursor.close();
		return filePath;
	}

	/*ublic String Disassemble(EditText result)
	 {
	 //String s=disassemble(filecontent, elfUtil.getEntryPoint());
	 String s;
	 byte [] b=Arrays.copyOfRange(filecontent, (int)elfUtil.getEntryPoint(), filecontent.length - 1);
	 s = new DisasmResult(b, 0).toString();
	 return s;
	 }
	 */
    private ProgressDialog showProgressDialog(String s)
	{
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setMessage(s);
		dialog.setCancelable(false);
        dialog.show();
		return dialog;
    }

	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes)
	{
		char[] hexChars = new char[bytes.length * 2];
		for (int p=0, j = 0; j < bytes.length; j++)
		{
			int v = bytes[j] & 0xFF;
			hexChars[p++] = hexArray[v >>> 4];
			hexChars[p++] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public void setShowAddress(boolean showAddress)
	{
		this.showAddress = showAddress;
	}

	public boolean isShowAddress()
	{
		return showAddress;
	}

	public void setShowLabel(boolean showLabel)
	{
		this.showLabel = showLabel;
	}

	public boolean isShowLabel()
	{
		return showLabel;
	}

	public void setShowBytes(boolean showBytes)
	{
		this.showBytes = showBytes;
	}

	public boolean isShowBytes()
	{
		return showBytes;
	}

	public void setShowInstruction(boolean showInstruction)
	{
		this.showInstruction = showInstruction;
	}

	public boolean isShowInstruction()
	{
		return showInstruction;
	}

	public void setShowCondition(boolean showCondition)
	{
		this.showCondition = showCondition;
	}

	public boolean isShowCondition()
	{
		return showCondition;
	}

	public void setShowOperands(boolean showOperands)
	{
		this.showOperands = showOperands;
	}

	public boolean isShowOperands()
	{
		return showOperands;
	}

	public void setShowComment(boolean showComment)
	{
		this.showComment = showComment;
	}

	public boolean isShowComment()
	{
		return showComment;
	}

    /* A native method that is implemented by the
     * 'hello-jni' native library, which is packaged
     * with this application.
     */
	//  public native String  disassemble(byte [] bytes, long entry);
	public native int Init();
	public native void Finalize();

    /* this is used to load the 'hello-jni' library on application
     * startup. The library has already been unpacked into
     * /data/data/com.example.hellojni/lib/libhello-jni.so at
     * installation time by the package manager.
     */
	static{
		System.loadLibrary("hello-jni");
	}

	/*	OnCreate()
	 vp = (ViewPager)findViewById(R.id.pager);
	 Button btn_first = (Button)findViewById(R.id.btn_first);
	 Button btn_second = (Button)findViewById(R.id.btn_second);
	 Button btn_third = (Button)findViewById(R.id.btn_third);

	 vp.setAdapter(new pagerAdapter(getSupportFragmentManager()));
	 vp.setCurrentItem(0);

	 btn_first.setOnClickListener(movePageListener);
	 btn_first.setTag(0);
	 btn_second.setOnClickListener(movePageListener);
	 btn_second.setTag(1);
	 btn_third.setOnClickListener(movePageListener);
	 btn_third.setTag(2);*/
	// Adapter 생성
	// adapter = new ListViewAdapter() ;
	/*	ListViewItem item=new ListViewItem();
	 item.setAddress("address");
	 item.setBytes("bytes");
	 item.setComments("comments");
	 item.setCondition("condition");
	 item.setInstruction("inst");
	 item.setLabel("label");
	 item.setOperands("operands");
	 adapter.addItem(item);
	 // 리스트뷰 참조 및 Adapter달기
	 listview = (ListView) findViewById(R.id.lvDisassembly);
	 listview.setAdapter(adapter);
	 listview.setOnTouchListener(new ListView.OnTouchListener() {
	 @Override
	 public boolean onTouch(View v, MotionEvent event) {
	 int action = event.getAction();
	 switch (action) {
	 case MotionEvent.ACTION_DOWN:
	 // Disallow ScrollView to intercept touch events.
	 v.getParent().requestDisallowInterceptTouchEvent(true);
	 break;

	 case MotionEvent.ACTION_UP:
	 // Allow ScrollView to intercept touch events.
	 v.getParent().requestDisallowInterceptTouchEvent(false);
	 break;
	 }

	 // Handle ListView touch events.
	 v.onTouchEvent(event);
	 return true;
	 }});
	 // 위에서 생성한 listview에 클릭 이벤트 핸들러 정의.
	 listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
	 @Override
	 public void onItemClick(AdapterView parent, View v, int position, long id) {
	 // get item
	 ListViewItem item = (ListViewItem) parent.getItemAtPosition(position) ;

	 //String titleStr = item.getTitle() ;
	 //String descStr = item.getDesc() ;
	 //Drawable iconDrawable = item.getIcon() ;

	 // TODO : use item data.
	 }
	 }) ;*/
	/*
	 PrintStackTrace to string
	 ByteArrayOutputStream out = new ByteArrayOutputStream();
	 PrintStream pinrtStream = new PrintStream(out);
	 e.printStackTrace(pinrtStream);
	 String stackTraceString = out.toString(); // 찍은 값을 가져오고.

	 */
	/*
	 public void onWindowFocusChanged(boolean hasFocus) {
	 // get content height
	 int contentHeight = listview.getChildAt(0).getHeight();
	 contentHeight*=listview.getChildCount();
	 // set listview height
	 LayoutParams lp = listview.getLayoutParams();
	 lp.height = contentHeight;
	 listview.setLayoutParams(lp);
	 }
	 */

	/*    switch(id) {
	 case R.id.menu_login:
	 Toast.makeText(getApplicationContext(), "로그인 메뉴 클릭",
	 Toast.LENGTH_SHORT).show();
	 return true;
	 case R.id.menu_logout:
	 Toast.makeText(getApplicationContext(), "로그아웃 메뉴 클릭",
	 Toast.LENGTH_SHORT).show();
	 return true;
	 case R.id.menu_a:
	 Toast.makeText(getApplicationContext(), "다음",
	 Toast.LENGTH_SHORT).show();
	 return true;
	 }*/
	/*
	 View.OnClickListener movePageListener = new View.OnClickListener()
	 {
	 @Override
	 public void onClick(View v)
	 {
	 int tag = (int) v.getTag();
	 vp.setCurrentItem(tag);
	 }
	 };

	 private class pagerAdapter extends FragmentStatePagerAdapter
	 {
	 public pagerAdapter(android.support.v4.app.FragmentManager fm)
	 {
	 super(fm);
	 }
	 @Override
	 public android.support.v4.app.Fragment getItem(int position)
	 {
	 switch(position)
	 {
	 case 0:
	 return new OverviewFragment();
	 case 1:
	 return new OverviewFragment();
	 case 2:
	 return new OverviewFragment();
	 default:
	 return null;
	 }
	 }
	 @Override
	 public int getCount()
	 {
	 return 3;
	 }
	 }*/
	//details.setText("file format not recognized.");
	//	String result=sample.getText().toString();
	//Toast toast = Toast.makeText(myActivity, result, Toast.LENGTH_LONG);
	//toast.show();
}
