package gossip;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Base64.Decoder;
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
    private static List<String> mensagensProcessadas = new ArrayList<String>();
    private static String arquivoProcurado;
    
    /**
     * Funcionalidade C
    * Thread que atualiza os arquivos das pastas a cada 30s
    */
	public static class ThreadAtualizaArquivosPasta extends Thread {
		public void run() {
			while(true) {
				preencheArquivosPasta();
				System.out.println("Sou peer " + meuIp + ":" + minhaPorta + " com arquivos " + String.join(", ", arquivos));
				try {
					Thread.sleep(300000); //30s
				} catch (Exception e) {
				}
			}
		}
	}
	
    /**
    * Thread de timeout para verificar se o arquivo buscado no SEARCH foi encontrado
    */
	public static class ThreadTimeoutArquivo extends Thread {
		public void run() {
			try {
				Thread.sleep(2000);
				if(arquivos.contains(arquivoProcurado) == false)
					System.out.println("ninguém no sistema possui o arquivo " + arquivoProcurado);
			} catch (Exception e) {}
		}
	}
	
   /**
    * Funcionalidade A e F
    * Thread que fica escutando em um socket UDP do peer
    * Recebendo mensagens do Tipo SEARCH e RESPONSE
    * No caso do tipo SEARCH, retorna uma RESPONSE com o arquivo para o peer caso tenha o arquivo procurado ou encaminha para um dos seus peers
    * No caso de receber um RESPONSE, salva o arquivo na pasta e adiciona ele na lista de arquivos
    */
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
				    		if(mensagemJaProcessada(mensagem)) {
								System.out.println("requisição já processada para " + mensagem.Corpo);
				    			break;
				    		}
				    		mensagensProcessadas.add(mensagem.Ip_Requisitante + ":" + mensagem.Porta_Requisitante + " " + mensagem.Corpo);
				    		
				    		if(arquivos.contains(mensagem.Corpo)) {
								System.out.println("tenho " + mensagem.Corpo + " respondendo para " + mensagem.Ip_Requisitante + ":" + mensagem.Porta_Requisitante);
								String base64File = encodeFileToBase64Binary(nomePasta + "/" + mensagem.Corpo);
								mensagem.Tipo = "RESPONSE";
								mensagem.Arquivo_Base64 = base64File;
								enviaMensagem(mensagem);
					    	} else {
					    		String nextPeer = enviaMensagem(mensagem);
								System.out.println("não tenho " + mensagem.Corpo + ", encaminhando para " +nextPeer );
					    	}
				    		break;
				    	case "RESPONSE":
				    		if(mensagemJaProcessada(mensagem)) {
								System.out.println("requisição já processada para " + mensagem.Corpo);
				    			break;
				    		}
				    		
				    		mensagensProcessadas.add(mensagem.Ip_Requisitante + ":" + mensagem.Porta_Requisitante + " " + mensagem.Corpo);
				    		
				    		byte[] file = decodeFileFromBase64Binary(mensagem.Arquivo_Base64);
				    		Path destinationFile = Paths.get(nomePasta, mensagem.Corpo);
				    		Files.write(destinationFile, file);
				    		arquivos.add(mensagem.Corpo);
				    		System.out.println("peer com arquivo procurado: "+ recPkt.getAddress().toString() + ":" + recPkt.getPort() +" "+ mensagem.Corpo);
				    }
				}
			} catch (Exception e) {
				System.out.println("Não foi possível escutar na porta: "+minhaPorta);
			}
		}
	}
	
    /**
    * Verifica se uma Mensagem já foi processada pelo peer
    *
    * @param  mensagem   O objeto mensagem
    * @return         	 true se já foi processado, false caso contrário
    */
    public static boolean mensagemJaProcessada(Mensagem mensagem) {
    	for(String m : mensagensProcessadas) {
    		String novaMensagem = mensagem.Ip_Requisitante + ":" + mensagem.Porta_Requisitante + " " + mensagem.Corpo;
    		if(novaMensagem.equals(m)) {
    			return true;
    		}
    	}
    	return false;
    }
	
    /**
    * Converte um arquivo binário em uma string base64
    *
    * @param  fileName   O nome do aruqivo a ser convertido para base64
    * @return         	 A string base64 do arquivo
    */
	private static String encodeFileToBase64Binary(String fileName) throws Exception {
	    byte[] byteData = Files.readAllBytes(Paths.get(fileName));
        String base64String = Base64.getEncoder().encodeToString(byteData);
        return base64String;
	}
	
    /**
    * Converte uma string base64 de um arquivo em um byte[] desse arquivo
    *
    * @param  fileBase64   A string base64 do arquivo
    * @return         	   Um byte[] do arquivo
    */
	private static byte[] decodeFileFromBase64Binary(String fileBase64) throws Exception {
        byte[] decodedFile = Base64.getDecoder()
                .decode(fileBase64.getBytes(StandardCharsets.UTF_8));
        return decodedFile;
	}
	
    /**
    * Lista todos os arquivos da pasta selecionada pelo peer e os salva na variável arquivos.
    */
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
	
    /**
    * Recebe um objeto Mensagem a ser enviado para outro peer
    * Sorteia dentre os dois peers que temos armazenado qual irá receber a mensagem
    * Cria o socket UDP do peer selecionado e usa a biblioteca GSON para converter o objeto Mensagem para uma string
    * Envia a string JSON criada para o peer selecionado
    *
    * @param  mensagem   O objeto mensagem que será enviado para outro peer
    * @return         	 Uma String com o ip e porta do peer que foi sorteado para receber a mensagem
    */
	public static String enviaMensagem(Mensagem mensagem) throws Exception {
	    Random rng = new Random();
	    String ipPeerTarget;
	    int portaPeerTarget;
	    
	    if(mensagem.Tipo == "SEARCH") {
		    if(rng.nextBoolean()) {
		    	ipPeerTarget = ipPeer1;
		    	portaPeerTarget = portaPeer1;
		    } else {
		    	ipPeerTarget = ipPeer2;
		    	portaPeerTarget = portaPeer2;
		    }
	    } else {
	    	ipPeerTarget = mensagem.Ip_Requisitante;
	    	portaPeerTarget = mensagem.Porta_Requisitante;
	    }
	    
    	DatagramSocket clientSocket = new DatagramSocket();
    	InetAddress IPAddress = InetAddress.getByName(ipPeer1);
    	
    	byte[] sendData = new byte[1024];
		sendData = gson.toJson(mensagem).getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, portaPeer1);
		clientSocket.send(sendPacket);
		return ipPeerTarget + ":" +portaPeerTarget;
	}
	
    /**
    * Funcionalidade B
    * Lê do teclado uma string com a pasta onde estão os arquivos, uma string com o Ip do peer usuário e um inteiro com a porta
    * Mais dois ips e portas dos demais peers
    *
    * @param  sc 		 O Scanner para ler do teclado
    */
	public static void obtemDadosTeclado(Scanner sc) {
	    //Obtem a pasta do peer
		System.out.println("Funcionalidade INICIALIZA selecionada\nInsira o nome da pasta:\n");
	    nomePasta = sc.nextLine();
	    
	    //Obtem o ip e porta do peer usuário
	    System.out.println("Insira o seu IP");
	    meuIp = sc.nextLine();
	    System.out.println("Insira a sua porta");
	    minhaPorta = Integer.parseInt(sc.nextLine());
	    
	    //Obtem o ip e porta de dois outros peers
	    System.out.println("Insira o IP do peer 1:");
	    ipPeer1 = sc.nextLine();
	    System.out.println("Insira a porta do peer 1:");
	    portaPeer1 =  Integer.parseInt(sc.nextLine());

	    System.out.println("Insira o IP do peer 2:");
	    ipPeer2 = sc.nextLine();
	    System.out.println("Insira a porta do peer 2:");
	    portaPeer2 =  Integer.parseInt(sc.nextLine());
	}
	
	public static void main(String[] args) throws Exception {
	    Scanner sc = new Scanner(System.in);
	    
	    while(true) {

		    System.out.println("Selecione uma funcionalidade:\n - INICIALIZA\n - SEARCH\n");
		    String funcao = sc.nextLine().toUpperCase();
		    
		    switch(funcao) {
		    	case "INICIALIZA":

		    		obtemDadosTeclado(sc);
		    	    preencheArquivosPasta();
		    	    
		    	    System.out.println("arquivos da pasta: "+ nomePasta + ": " + String.join(", ", arquivos));
				    
		    	    //Cria a thread responsável por atualizar os arquivos da pasta a cada 30s
				    Thread threadAtualizaPastas = new ThreadAtualizaArquivosPasta();
				    threadAtualizaPastas.start();
				    
		    	    //Cria a thread responsável por ficar escutando requisições UDP
				    Thread threadRecebeRequests = new ThreadRecebeRequest();
				    threadRecebeRequests.start();
				    
		    		break;

		    	case "SEARCH":
				    System.out.println("Funcionalidade SEARCH selecionada\nInsira o nome do arquivo com extensão:\n");
				    String arquivoBusca = sc.nextLine();

		    		//Funcionalidade B
		    		//Envia uma mensagem para um peer aleatório
			    	Mensagem mensagem = new Mensagem();
			    	mensagem.Corpo = arquivoBusca;
					mensagem.Tipo = "SEARCH";
					mensagem.Ip_Requisitante = meuIp;
					mensagem.Porta_Requisitante = minhaPorta;

					enviaMensagem(mensagem);
					arquivoProcurado = arquivoBusca;
					

		    		//Funcionalidade G
					//Cria a thread que fica esperando um tempo para saber se o arquivo nunca será recebido
					Thread threadTimeoutArquivo = new ThreadTimeoutArquivo();
					threadTimeoutArquivo.start();
					
		    		break;
		    		
		    	default:
		    		System.out.println("Funcionalidade invalida");
		    		break;
		    }
	    }

	}
}
