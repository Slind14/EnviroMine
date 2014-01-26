package enviromine.gui;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

import net.minecraft.entity.player.EntityPlayer;
import cpw.mods.fml.common.IPlayerTracker;
import enviromine.core.EM_Settings;

public class UpdateNotification implements IPlayerTracker
{

	@Override
	public void onPlayerLogin(EntityPlayer player) 
	{
		String[] data;
		try {
			data = getNotification();

		
		String version = data[0].trim();
		String http = data[1].trim();
		String comments = data[2];
		
		player.addChatMessage("�eCurrent Version  "+ EM_Settings.Version);
		player.addChatMessage("�r"+comments);

		if(!(EM_Settings.Version.equals(version)))
		{
			player.addChatMessage("�cUpdate "+ version  +" of EnviroMine is available");
			player.addChatMessage("�rDownload:�9�n " + http);
			player.addChatMessage("�r");
		}
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void onPlayerLogout(EntityPlayer player) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onPlayerChangedDimension(EntityPlayer player) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onPlayerRespawn(EntityPlayer player) {
		// TODO Auto-generated method stub
		
	}
	
	private String[] getNotification() throws IOException
	{
		URL url = new URL("https://dl.dropboxusercontent.com/u/44796802/enviromine/EnviromineLauncer.txt");
		HttpURLConnection.setFollowRedirects(true);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setDoOutput(false);
		con.setReadTimeout(20000);
		con.setRequestProperty("Connection", "keep-alive");

		con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:16.0) Gecko/20100101 Firefox/16.0");
		((HttpURLConnection) con).setRequestMethod("GET");
    //	System.out.println(con.getContentLength()) ;
		con.setConnectTimeout(5000);
		BufferedInputStream in = new BufferedInputStream(con.getInputStream());
		int responseCode = con.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_OK) {
			System.out.println(responseCode);
		}
		StringBuffer buffer = new StringBuffer();
		int chars_read;
    //	int total = 0;
		while ((chars_read = in.read()) != -1) 
		{	
			char g = (char) chars_read;
			buffer.append(g);
		}
		final String page = buffer.toString();

		String[] pageSplit = page.split("\\n");
		
		return pageSplit;
	}
}
