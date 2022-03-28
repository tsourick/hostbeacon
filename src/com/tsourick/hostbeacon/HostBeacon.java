package com.tsourick.hostbeacon;

import com.sun.xml.internal.fastinfoset.util.StringArray;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HostBeacon
{
	static int iIntervalMin = 1, iIntervalMax = 99;

	private static void log(String text)
	{
		System.out.println(text);
	}

	private static int getInterval()
	{
		int iInterval;


		String sInterval = properties.getProperty("interval", "15");

		// TODO: implement consistent validation bound with formatter


		iInterval = Integer.parseInt(sInterval);

		try
		{
			if (iInterval < iIntervalMin || iInterval > iIntervalMax) throw new NumberFormatException("Out of bounds");
		}
		catch (NumberFormatException e)
		{
			iInterval = iIntervalMin;
		}

		return iInterval;
	}


	private static String ping(String urlString, Properties params)
	{
		// Make URL

		ArrayList<String> pairs = new ArrayList<String>();
		for (String name : params.stringPropertyNames())
		{
			String value = params.getProperty(name);

			pairs.add(name + "=" + value);
		}

		urlString += (urlString.contains("?") ? "&" : "?") + String.join("&", pairs);

		System.out.println(String.format("ready to ping at %s", urlString));

		StringBuilder result = new StringBuilder();

		try
		{
			log("Run URLConnection");

			URL url = new URL(urlString);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			// conn.setDoOutput(true);
			// conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Content-Type", "text/plain");
			conn.setRequestProperty("charset", "utf-8");

			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

			String line;
			while ((line = reader.readLine()) != null)
			{
				System.out.println(line);
				result.append(line);
			}
			reader.close();

			log("response saved");
		}
		catch (MalformedURLException e)
		{
			log("MalformedURLException: " + e.getMessage());
		}
		catch (java.io.IOException e)
		{
			log("java.io.IOException: " + e.getMessage());
		}

		return result.length() > 0 ? result.toString() : null;
	}


	private static class PingerRunnable implements Runnable
	{
		private long lastTime = 0;
		static int pos = 0;

		public void run()
		{
			long nowTime = System.currentTimeMillis();

			long delta = lastTime > 0 ? nowTime - lastTime : 0;

			log("PingerRunnable ("+pos+") after delta:" + delta);
			pos++;

			lastTime = nowTime;


			String urlString = properties.getProperty("base");
			String params = properties.getProperty("params");

			Properties cgiParams = new Properties();
			String[] names = params.split(",");
			for (int i = 0; i < names.length; i++)
			{
				String name = names[i];
				String value = properties.getProperty("param_" + name, "");
				cgiParams.setProperty(name, value);
			}

			ping(urlString, cgiParams);
			// if (ping(urlString, cgiParams) == null) stop();

			//try{Thread.sleep(1700);}catch(InterruptedException e){}
		}
	}

	private static PingerRunnable pr = new PingerRunnable();


	private static ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
	private static ScheduledFuture sef = null;
	private static Object sefLock = new StringBuilder("sef");

	public static boolean start()
	{
		boolean result = false;

		synchronized (sefLock)
		{
			if (sef == null)
			{
				log("STARTING");

				String urlString = properties.getProperty("interval");

				// sef = ses.scheduleWithFixedDelay(pr, 0, 1, TimeUnit.SECONDS);
				sef = ses.scheduleWithFixedDelay(pr, 0, getInterval(), TimeUnit.SECONDS);
				result = true;
			}
		}

		return result;
	}

	public static boolean stop ()
	{
		boolean result = false;

		synchronized (sefLock)
		{
			if (sef != null)
			{
				sef.cancel(false);
				sef = null;

				log("STOPPED");

				result = true;
			}
		}

		return result;
	}

	static void finish()
	{
		stop(); // ensure stop

		System.exit(0);
	}

	private static Properties properties = new Properties();

	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				// Temporary variables
				JPanel panel;
				JLabel label;

				try
				{
					String path = "HostBeacon.properties";
					Properties props = properties;
					props.load(new FileInputStream(path));

					for (String key : props.stringPropertyNames())
					{
						String value = props.getProperty(key);

						log(key + "=" + value);
					}

					String title = props.getProperty("title");

					// GUI
					JFrame frame = new JFrame((! Objects.equals(title, "") ? String.format("[%s] - ", title) : "") + "HostBeacon");
					frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
					frame.setSize(new Dimension(650, 650));
					frame.setLayout(new FlowLayout());

					frame.addWindowListener(new WindowAdapter()
					{
						@Override
						public void windowClosing(WindowEvent e)
						{
							super.windowClosing(e);

							finish();
						}
					});

					JPopupMenu ppm = new JPopupMenu();
					JMenuItem mi;
					mi = new JMenuItem(new DefaultEditorKit.CutAction());
					mi.setText("Cut");
					ppm.add(mi);
					mi = new JMenuItem(new DefaultEditorKit.CopyAction());
					mi.setText("Copy");
					ppm.add(mi);
					mi = new JMenuItem(new DefaultEditorKit.PasteAction());
					mi.setText("Paste");
					ppm.add(mi);

					// Parse params
					String params = props.getProperty("params");

					JPanel masterPanel = new JPanel();
					masterPanel.setLayout(new BoxLayout(masterPanel, BoxLayout.Y_AXIS));

					// Service row

					panel = new JPanel();
					panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
					// panel.setBorder(new LineBorder(Color.BLACK));

					label = new JLabel("Service:");
					label.setPreferredSize(new Dimension(90, 18));
					// label.setBorder(new LineBorder(Color.RED));
					// label.setHorizontalTextPosition(SwingConstants.RIGHT);
					label.setHorizontalAlignment(SwingConstants.RIGHT);
					panel.add(label);

					panel.add(Box.createHorizontalStrut(10));

					label = new JLabel(props.getProperty("title"));
					// label.setBorder(new LineBorder(Color.RED));
					label.setFont(label.getFont().deriveFont(Font.PLAIN));
					panel.add(label);

					panel.add(Box.createHorizontalGlue());


					masterPanel.add(panel);
					masterPanel.add(Box.createVerticalStrut(2));


					// At row

					panel = new JPanel();
					panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
					// panel.setBorder(new LineBorder(Color.BLACK));

					label = new JLabel("URL:");
					label.setPreferredSize(new Dimension(90, 18));
					// label.setBorder(new LineBorder(Color.RED));
					label.setHorizontalAlignment(SwingConstants.RIGHT);
					panel.add(label);

					panel.add(Box.createHorizontalStrut(10));

					label = new JLabel(props.getProperty("base"));
					// label.setBorder(new LineBorder(Color.RED));
					label.setFont(label.getFont().deriveFont(Font.PLAIN));
					panel.add(label);

					panel.add(Box.createHorizontalGlue());


					masterPanel.add(panel);
					masterPanel.add(Box.createVerticalStrut(8));


					String[] names = params.split(",");
					for (int i = 0; i < names.length; i++)
					{
						String name = names[i];

						String value = props.getProperty("param_" + name, "<undefined>");

						panel = new JPanel();
						panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

						label = new JLabel(name + ":");
						label.setPreferredSize(new Dimension(100, 0));
						panel.add(label);

						JTextField tf = new JTextField(value, 20)
						{
							private void createMouseListener() {
								this.addMouseListener(new java.awt.event.MouseAdapter() {
									@Override
									public void mousePressed(java.awt.event.MouseEvent evt) {
										requestFocusInWindow();
									}

									@Override
									public void mouseReleased(java.awt.event.MouseEvent evt) {
										requestFocusInWindow();
									}

									@Override
									public void mouseClicked(java.awt.event.MouseEvent evt) {
										requestFocusInWindow();
									}
								});
							}

							{
								createMouseListener();
							}
						};
						tf.setPreferredSize(new Dimension(100, 22)); // width is ignored here
						tf.setBorder(BorderFactory.createCompoundBorder(
							tf.getBorder(),
							BorderFactory.createEmptyBorder(0, 2, 0, 2)));

						tf.setComponentPopupMenu(ppm);
						panel.add(tf);

						masterPanel.add(panel);
						if (i < names.length - 1) masterPanel.add(Box.createVerticalStrut(4));
					}

					frame.add(masterPanel);


					// Buttons

					masterPanel.add(Box.createVerticalStrut(12));

					JPanel ctrlPanel = new JPanel();
					ctrlPanel.setLayout(new BoxLayout(ctrlPanel, BoxLayout.X_AXIS));


					JPanel pp = new JPanel();
					pp.setLayout(new BoxLayout(pp, BoxLayout.X_AXIS));
					// pp.setBorder(BorderFactory.createLineBorder(Color.BLACK));
					pp.setAlignmentY(Box.CENTER_ALIGNMENT);
					Dimension dd = new Dimension(125, 26);
					pp.setPreferredSize(dd);pp.setMaximumSize(dd);pp.setMinimumSize(dd);

					pp.add(Box.createHorizontalGlue());


					JLabel lb = new JLabel("ON");
					lb.setForeground(new Color(0x30, 0xbb, 0x30));
					lb.setVisible(false);
					pp.add(lb);
					final JComponent progressIndicatorOn = lb;

					lb = new JLabel("OFF");
					// lb.setForeground(new Color(0xbb, 0x30, 0x30));
					lb.setForeground(new Color(0xBB, 0xBB, 0xBB));
					lb.setVisible(true);
					pp.add(lb);
					final JComponent progressIndicatorOff = lb;

					// pp.add(Box.createHorizontalStrut(25));
					pp.add(Box.createHorizontalGlue());


					ctrlPanel.add(pp);

					final JToggleButton theButton = new JToggleButton("PING"){{
						addItemListener(new ItemListener()
						{
							@Override
							public void itemStateChanged(ItemEvent e)
							{
								JToggleButton btn = (JToggleButton)e.getSource();

								if (e.getStateChange() == ItemEvent.SELECTED)
								{
									progressIndicatorOn.setVisible(true);
									progressIndicatorOff.setVisible(false);

									start();
								}
								else if (e.getStateChange() == ItemEvent.DESELECTED)
								{
									stop();

									progressIndicatorOn.setVisible(false);
									progressIndicatorOff.setVisible(true);
								}
							}
						});
					}};
					ctrlPanel.add(theButton);

					ctrlPanel.add(Box.createHorizontalGlue());

					ctrlPanel.add(Box.createHorizontalStrut(10));

					// "Every" interval

					int interval = getInterval();

					ctrlPanel.add(new JLabel("every "));
					NumberFormatter fmtr = new NumberFormatter(new DecimalFormat("##"));
					fmtr.setAllowsInvalid(false);
					fmtr.setOverwriteMode(true);
					fmtr.setMinimum(iIntervalMin);
					fmtr.setMaximum(iIntervalMax);
					JTextField tf = new JFormattedTextField(fmtr);
					tf.setBorder(BorderFactory.createCompoundBorder(
						tf.getBorder(),
						BorderFactory.createEmptyBorder(0, 2, 0, 2)));

					Dimension dim = new Dimension(24, 22);
					tf.setPreferredSize(dim);tf.setMinimumSize(dim);tf.setMaximumSize(dim);
					// tf.setMaximumSize(dim); tf.setMinimumSize(dim);

					// tf.setText(sInterval);
					tf.setText(new Integer(interval).toString());

					ctrlPanel.add(tf);
					ctrlPanel.add(new JLabel(" sec (debug)"));

					ctrlPanel.add(Box.createHorizontalGlue());

					masterPanel.add(ctrlPanel);
					masterPanel.add(Box.createVerticalStrut(8));


					frame.pack();
					frame.setLocationRelativeTo(null);

					frame.setVisible(true);

					frame.setMinimumSize(frame.getSize());


					// TODO: make the code compact
					ArrayList<String> yesValues = new ArrayList<String>();
					yesValues.add("1");
					yesValues.add("on");
					yesValues.add("yes");
					yesValues.add("true");

					String sAutostart = props.getProperty("autostart");

					final Boolean bAutostart = yesValues.indexOf(sAutostart) != -1;
					// log(bAutostart.toString());
					frame.addWindowListener(new WindowAdapter()
					{
						@Override
						public void windowOpened(WindowEvent e)
						{
							super.windowOpened(e);

							if (bAutostart) theButton.doClick();
						}
					});
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		});
	}
}
