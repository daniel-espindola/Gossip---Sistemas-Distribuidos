package gossip;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import com.google.gson.Gson;

public class Peer {
	private static String nomePasta;
	private static List<String> arquivos;
	private static String meuIp;
	private static int minhaPorta;
	private static String ipPeer1;
	private static int portaPeer1;
	private static String ipPeer2;
	private static int portaPeer2;
    private static Gson gson = new Gson();
	
	public static class ThreadAtualizaArquivosPasta extends Thread {
		public void run() {
			while(true) {
				preencheArquivosPasta();
				System.out.println("Sou peer " + meuIp + ":" + minhaPorta + " com arquivos " + String.join(", ", arquivos));
				try {
					Thread.sleep(30000); //30s
				} catch (Exception e) {
				}
			}
		}
	}
	
	public static class ThreadRecebeRequest extends Thread {
		public void run() {

			DatagramSocket serverSocket;
			try {
				serverSocket = new DatagramSocket(minhaPorta);
				
				while (true) {
					byte[] recBuffer = new byte[1024];
					DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
					serverSocket.receive(recPkt);
			
					String requestStr = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
				    Mensagem mensagem = gson.fromJson(requestStr, Mensagem.class);
					
				    switch(mensagem.Tipo) {
				    	case "SEARCH":
				    		if(arquivos.contains(mensagem.Corpo)) {
								System.out.println("tenho " + mensagem.Corpo + " respondendo para " + mensagem.Ip_Requisitante + ":" + mensagem.Porta_Requisitante);
					    	} else {
					    		String nextPeer = enviaMensagem(mensagem);
								System.out.println("não tenho " + mensagem.Corpo + ", encaminhando para " +nextPeer );
					    	}
				    		break;
				    	case "RESPONSE":
				    		System.out.println("peer com arquivo procurado: "+ mensagem.Ip_Requisitante + ":" + mensagem.Porta_Requisitante +" "+ mensagem.Corpo);
				    }
				}
			} catch (Exception e) {
				System.out.println("Não foi possível escutar na porta: "+minhaPorta);
			}
		}
	}
	
	public static void preencheArquivosPasta() {
		//REFERÊNCIA: https://stackoverflow.com/questions/5694385/getting-the-filenames-of-all-files-in-a-folder
		arquivos = new ArrayList<String>();
		File f1 = new File(nomePasta);
		if(f1.isDirectory()) {
			File[] files = f1.listFiles();
			for (File file : files) {
			    if (file.isFile()) {
			    	arquivos.add(file.getName());
			    }
			}
		} else {
			System.out.println("Pasta nao encontrada!");
		}
	}
	
	public static String enviaMensagem(Mensagem mensagem) throws Exception {
	    Random rng = new Random();
	    
	    String ipPeerTarget;
	    int portaPeerTarget;
	    if(rng.nextBoolean()) {
	    	ipPeerTarget = ipPeer1;
	    	portaPeerTarget = portaPeer1;
	    } else {
	    	ipPeerTarget = ipPeer2;
	    	portaPeerTarget = portaPeer2;
	    }
	    
    	DatagramSocket clientSocket = new DatagramSocket();
    	InetAddress IPAddress = InetAddress.getByName(ipPeer1);
    	
    	byte[] sendData = new byte[1024];
		sendData = gson.toJson(mensagem).getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, portaPeer1);
		clientSocket.send(sendPacket);
		return ipPeerTarget + ":" +portaPeerTarget;
	}
	
	public static void main(String[] args) throws Exception {
	    Scanner sc = new Scanner(System.in);
	    
	    while(true) {

		    System.out.println("Selecione uma funcionalidade:\n - INICIALIZA\n - SEARCH\n");
		    String funcao = sc.nextLine().toUpperCase();
		    
		    switch(funcao) {
		    	case "INICIALIZA":
		    		
		    		//OBTEM OS ARQUIVOS DA PASTA
				    System.out.println("Funcionalidade INICIALIZA selecionada\nInsira o nome da pasta:\n");
				    nomePasta = sc.nextLine();
				    preencheArquivosPasta();
				    System.out.println("arquivos da pasta: "+ nomePasta + ": " + String.join(", ", arquivos));
				    
				    
				    System.out.println("Insira o seu IP");
				    meuIp = sc.nextLine();
				    System.out.println("Insira a sua porta");
				    minhaPorta = Integer.parseInt(sc.nextLine());
				    
				    System.out.println("Insira o IP do peer 1:");
				    ipPeer1 = sc.nextLine();
				    System.out.println("Insira a porta do peer 1:");
				    portaPeer1 =  Integer.parseInt(sc.nextLine());

				    System.out.println("Insira o IP do peer 2:");
				    ipPeer2 = sc.nextLine();
				    System.out.println("Insira a porta do peer 2:");
				    portaPeer2 =  Integer.parseInt(sc.nextLine());
				    
				    Thread threadAtualizaPastas = new ThreadAtualizaArquivosPasta();
				    threadAtualizaPastas.start();
				    
				    Thread threadRecebeRequests = new ThreadRecebeRequest();
				    threadRecebeRequests.start();
				    
		    		break;

		    	case "SEARCH":
				    System.out.println("Funcionalidade SEARCH selecionada\nInsira o nome do arquivo com extensão:\n");
				    String arquivoBusca = sc.nextLine();
				    
			    	Mensagem mensagem = new Mensagem();
			    	mensagem.Corpo = arquivoBusca;
					mensagem.Tipo = "SEARCH";
					mensagem.Ip_Requisitante = meuIp;
					mensagem.Porta_Requisitante = minhaPorta;

					enviaMensagem(mensagem);
					
					//Tratamento para verificar se eu nunca recebi uma response
					
		    		break;
		    		
		    	default:
		    		System.out.println("Funcionalidade invalida");
		    		break;
		    }
	    }

	}
}
