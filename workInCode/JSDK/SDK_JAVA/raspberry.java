import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Map;

import com.kt.smcp.gw.ca.comm.exception.SdkException;
import com.kt.smcp.gw.ca.gwfrwk.adap.stdsys.sdk.tcp.BaseInfo;
import com.kt.smcp.gw.ca.gwfrwk.adap.stdsys.sdk.tcp.IMCallback;
import com.kt.smcp.gw.ca.gwfrwk.adap.stdsys.sdk.tcp.IMTcpConnector;
import com.kt.smcp.gw.ca.util.IMUtil;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.PinPullResistance;

public class raspberry extends IMCallback  
{
	private GpioController gpio = null;
	private GpioPinDigitalOutput pin1 = null;
	private static GpioPinDigitalInput pin2 = null;

	public raspberry()
	{
		// get a handle to the GPIO controller...
		gpio = GpioFactory.getInstance();

		// creating the pin with parameter PinState.HIGH...
		// will instantly power up the pin...
		pin1 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_05, "PinLED", PinState.HIGH);
		pin2 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04,"PinTC", PinPullResistance.PULL_DOWN);

	}

	public static void main(String[] args) throws Exception 
	{
		// callback fuction call...
		raspberry callback = new raspberry();
		IMTcpConnector tcpConnector = new IMTcpConnector();
		BaseInfo baseInfo = null;

		Long transID;
		Long timeOut = (long)3000;

		try 
		{
			baseInfo = IMUtil.getBaseInfo("IoTSDK.properties");
			tcpConnector.init(callback, baseInfo);

			tcpConnector.connect(timeOut);	
			tcpConnector.authenticate(timeOut);			

			while(true)
			{				
				transID = IMUtil.getTransactionLongRoundKey4();

				// Temp Teg value send...
				tcpConnector.requestStrColecData("NFCIN", getValue(), new Date(), transID);
				Thread.sleep(1000);

			}

		} catch(SdkException e) 
		{
			System.out.println("Code :" + e.getCode() + " Message :" + e.getMessage());
		}
	}

	// Temp sensor value...
	private static String getValue() throws Exception 
	{
		// run 'Python process' and get Temp value... 
		Runtime run = Runtime.getRuntime();
		Process proc= run.exec("nfc-poll");
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

		Double temperature;

		// read the output from the command...
		String s = null;
		String sOut = "";

		while((s = stdInput.readLine()) != null) 
		{	
			s = s.trim();
			if(s.startsWith("UID")){
				s = s.split(":")[1].trim().replace("  ", "");
				sOut = sOut + s;
			}
		}
		if(!(sOut.contains("ERR_RANGE") || sOut.contains("ERR_CRC"))) 
		{
			System.out.println(sOut);
			return sOut;
		} 
		else
		{
			return null;
		}
	}

	@Override
	public void handleColecRes(Long transId, String respCd) 
	{
		System.out.println("Collect Response. Transaction ID :" + transId + " Response Code : " + respCd);	
	}

	@Override
	public void handleControlReq(Long transID, Map<String, Double> numberRows, Map<String, String> stringRows) 
	{	
		System.out.println("Handle Control Request Called. Transaction ID : " + transID);
		System.out.println(numberRows.size()+" Number Type controls. " + stringRows.size() + " String Type controls.");
		
		if(numberRows.size() > 0) 
		{
			for(String key : numberRows.keySet()) 
			{
				System.out.println("Tag Stream :" + key + " Value:" + numberRows.get(key));
			}
		}

		// LED control value form IoTMakers...
		if(stringRows.size() > 0) 
		{
			for(String key : stringRows.keySet()) 
			{
				System.out.println("Tag Stream :" + key + " Value:" + stringRows.get(key));
				tcpConnector.requestStrColecData(key, stringRows.get(key), new Date(), transID);
				if("LED".equals(key))
				{
					if("ON".equals(stringRows.get(key)))
					{
						System.out.println("LED ON");
						pin1.high();
					}
					else if("OFF".equals(stringRows.get(key)))
					{
						System.out.println("LED OFF");
						pin1.low();
					}
				}
			}
		}
	}
}