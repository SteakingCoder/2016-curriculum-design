import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Equim on 16-11-27.
 * 向Java势力低头 vol.1
 */
public class Server extends JFrame
{
    private final boolean ShowSplashScreen = true;    //是否显示SplashScreen

    private SimpleAttributeSet attrSet = new SimpleAttributeSet();  //文字属性
    private Document publicDoc;           //会在Init_UI中初始化

    private JTextField inputTextBox = new JTextField();
    private JTextPane logArea = new JTextPane();                    //为了支持每行不同颜色
    private JScrollPane scrollPane = new JScrollPane(logArea);
    private JButton textButton = new JButton("发送");
    private JList<String> onlineList = new JList<>();
    private DefaultListModel<String> onlineListModel = new DefaultListModel<>();

    private final Color systemTextColor = new Color(0, 204, 51);          //系统信息的颜色，本来想用enum，结果又感觉不合适
    private final Color activityTextColor = new Color(0, 163, 204);       //上下线信息的颜色
    private final Color monitorTextColor = new Color(102, 0, 102);        //群主发的消息的颜色
    private final Color generalTextColor = new Color(0, 0, 0);            //一般消息的颜色

    private MyListCellRenderer notifyRenderer = new MyListCellRenderer();

//    private JTabbedPane tabPane = new JTabbedPane(JTabbedPane.TOP);       //放弃尝试了

    private MenuBar serverMenu = new MenuBar();
    private Menu m_help = new Menu("Help");
    private MenuItem m_about = new MenuItem("About");
    private Menu m_file = new Menu("File");
    private MenuItem m_sendFile = new MenuItem("Send File to Selected Client...");

    private ServerSocket server;
    private LinkedList<Socket> clientList = new LinkedList<Socket>();
    private Map<String, Socket> nameSocketMap = new HashMap<>();
    private Map<String, Document> nameDocMap = new HashMap<>();

    private PrintStream ps;
    private File fileToSend = null;

    private String getTimeStamp()
    {
        return new SimpleDateFormat("[hh:mm:ss] ").format(new Date());
    }

    //
    //  UI初始化
    //
    private void Init_UI()
    {
        // 窗体
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setSize(400, 550);
        this.setTitle("服务端");

        // 设置聊天记录的默认字体等
        publicDoc = logArea.getStyledDocument();
        StyleConstants.setFontSize(attrSet, 14);
//        StyleConstants.setFontFamily(attrSet, "微软雅黑");
        logArea.setEditable(false);
        this.add(scrollPane, BorderLayout.CENTER);      //TODO: 滚动条自动滑动到最底

        // 设置左边的在线列表
        onlineListModel.addElement("公频");
        onlineList.setModel(onlineListModel);
        onlineList.setCellRenderer(notifyRenderer);
        onlineList.setBackground(Color.lightGray);
        onlineList.setSelectedIndex(0);
        this.add(onlineList, BorderLayout.WEST);

        // 设置下面的输入栏
        inputTextBox.setFont(inputTextBox.getFont().deriveFont(14F));
        textButton.setFont(new Font("微软雅黑", textButton.getFont().getStyle(), textButton.getFont().getSize()));
        JPanel bottom = new JPanel();
        bottom.setLayout(new BorderLayout());
        bottom.add(inputTextBox, BorderLayout.CENTER);
        bottom.add(textButton, BorderLayout.EAST);
        this.add(bottom, BorderLayout.SOUTH);

        // 菜单
        m_file.add(m_sendFile);
        m_sendFile.addActionListener(new ActionListener() {             //TODO: 考虑新写个类，因为这个内容有点多
            @Override
            public void actionPerformed(ActionEvent e) {
                if (onlineList.getSelectedIndex() == 0)
                {
                    JOptionPane.showMessageDialog(null, "你没有选中一个客户端", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                JFileChooser jfc = new JFileChooser();
                jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
                if (jfc.showOpenDialog(new Label("请选择要发送的文件")) == JFileChooser.CANCEL_OPTION)
                    return;
                fileToSend = jfc.getSelectedFile();
                try
                {
                    //TODO: 靠消息让指定客户端建立33284端口的连接
                    // 然后让客户端选择是否要接收，并在原2333端口发送flag(收不收)。
                    // 如果是，则在客户端让其选择接收路径，建立各种stream。
                    // 33284只接受一个请求(线程里不用while(true))
                    // 发送完毕后，关闭33284。
                    //TODO: 客户端到客户端的情况
                    // 先由服务器转发请求到接收方
                    // 确定要收后，开启33284，只接收两个socket，注意，两个客户端必须发送标识，以区分发送和接收
                    // 将发送方的stream直接转发给接收方
                    // 发送完毕后，发送方关闭socket，接着服务端关闭33284
                    ServerSocket fileSocket = new ServerSocket(33284);
                    DataOutputStream dout = new DataOutputStream(nameSocketMap.get(onlineList.getSelectedValue()).getOutputStream());
                    FileInputStream fin = new FileInputStream(fileToSend);
                    Byte[] buffer = new Byte[1024];         // 1KB的buffer

                }
                catch(Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        });
        m_help.add(m_about);
        m_about.addActionListener(new HTML_About());
        serverMenu.add(m_file);
        serverMenu.add(m_help);
        this.setMenuBar(serverMenu);

        // 完成所有UI初始化
        this.setVisible(true);
    }

    //
    //  构造器
    //
    public Server()
    {
        try
        {
            // 显示SplashScreen
            if (ShowSplashScreen)
            {
                EqSplashScreen splash = new EqSplashScreen(this);
                splash.setVisible(true);
                //Thread.sleep(1500);
            }

            this.Init_UI();

            server = new ServerSocket(2333);
//            logArea.setForeground(systemMsg);
//            logArea.append(getTimeStamp() + "服务器已启动。 (127.0.0.1:2333)\n");
            StyleConstants.setForeground(attrSet, systemTextColor);
            publicDoc.insertString(publicDoc.getLength(), getTimeStamp() + "服务器已启动。(127.0.0.1:2333)\n", attrSet);

            // 发送群主的消息，可能是公频也可能是私聊
            ActionListener sendText = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!inputTextBox.getText().isEmpty())        //其实这里会触发bug，不知道为什么，有几个socket就会发几次  //已解决
                    {
                        try
                        {
                            String sendToWho = onlineList.getSelectedValue().toString();
                            if (sendToWho.equals("公频"))
                            {
                                StyleConstants.setForeground(attrSet, monitorTextColor);
                                publicDoc.insertString(publicDoc.getLength(),
                                        getTimeStamp() + "群主: " + inputTextBox.getText() +"\n", attrSet);

                                //遍历socket list群发
                                for(Socket clientIter : clientList)
                                {
                                    ps = new PrintStream(clientIter.getOutputStream());
                                    ps.println("monitor&" + inputTextBox.getText());
                                }
                            }
                            else
                            {
                                // 在私聊doc更新
                                Document currentQueryDoc = logArea.getDocument();
                                StyleConstants.setForeground(attrSet, monitorTextColor);
                                currentQueryDoc.insertString(currentQueryDoc.getLength(),
                                        getTimeStamp() + "群主: " + inputTextBox.getText() +"\n", attrSet);

                                // 发送给对方
                                ps = new PrintStream(nameSocketMap.get(sendToWho).getOutputStream());
                                ps.println("query&群主&" + inputTextBox.getText());
                            }

                            inputTextBox.setText("");         //上一段可能有延迟，所以这里本来可以小小地优化一下的
                        }
                        catch(Exception ex)
                        {
                            ex.printStackTrace();
                        }
                    }
                }
            };
            inputTextBox.addActionListener(sendText);
            textButton.addActionListener(sendText);

            // 处理左边tab的单击响应
            onlineList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent e) {
                    if (!onlineList.getValueIsAdjusting())
                    {
                        // 私聊给谁
                        String queryTo = onlineList.getSelectedValue().toString();

                        // 撤销高亮
                        notifyRenderer.remove(queryTo);
                        onlineList.updateUI();

                        if (queryTo.equals("公频"))
                        {
                            //mainDoc.insertString(mainDoc.getLength(), doc.getText(0, doc.getLength()), attrSet);      //不可取
                            logArea.setDocument(publicDoc);
                        }
                        else
                        {
                            logArea.setDocument(nameDocMap.get(queryTo));
                        }
                    }
                }
            });

            // 该线程用于监听新连接
            new Thread(new Runnable() {                               //Java真麻烦，好想用BeginAccept
                @Override
                public void run() {
                    try
                    {
                        while (true)
                        {
                            Socket newClient = server.accept();              //好想用BeginAccept()
                            clientList.add(newClient);
                            //logArea.append("有人上线");

                            // 该线程用于响应某个socket
                            new Thread(new Runnable() {
                                @Override
                                public void run() {                         //接收并解析
                                    try
                                    {
                                        while (true)
                                        {
                                            String receivedMsg = new BufferedReader(new InputStreamReader(newClient.getInputStream())).readLine();

                                            //解析字符串：
                                            // new&username                     | 添加新用户名
                                            // public&username&msg              | 公频发言
                                            // toMonitor&username&msg           | 给群主的发言
                                            // query&username&toUsername&msg    | 私聊发言
                                            // fileFlag&username&toUsername     | 传文件请求
                                            //不敢另开方法来解决，是怕遇到死锁
                                            String[] analyzedMsgs = receivedMsg.split("&");
                                            if (analyzedMsgs[0].equals("new"))
                                            {
                                                // 为新客户端发送当前在线列表
                                                ps = new PrintStream(newClient.getOutputStream());
                                                ps.print("onlineList&群主&");
                                                for(HashMap.Entry<String, Socket> nicknameIter : nameSocketMap.entrySet())
                                                {
                                                    ps.print(nicknameIter.getKey() + "&");
                                                }
                                                ps.println();

                                                //建立nickname到socket的map
                                                nameSocketMap.put(analyzedMsgs[1], newClient);
                                                onlineListModel.addElement(analyzedMsgs[1]);

                                                // 添加私聊用的doc，并和name映射
                                                nameDocMap.put(analyzedMsgs[1], new DefaultStyledDocument());

                                                // 为其他客户端发送这个新人上线的消息
                                                for(Socket clientIter : clientList)
                                                {
                                                    ps = new PrintStream(clientIter.getOutputStream());
                                                    ps.println("online&" + analyzedMsgs[1]);
                                                }

                                                // 公频刷新
                                                StyleConstants.setForeground(attrSet, activityTextColor);
                                                publicDoc.insertString(publicDoc.getLength(),
                                                        getTimeStamp() + "【" + analyzedMsgs[1] + "】刚刚上线了\n", attrSet);

                                            }
                                            else if (analyzedMsgs[0].equals("public"))
                                            {
                                                if (!onlineList.getSelectedValue().toString().equals("公频"))      //给tab加高亮
                                                {
                                                    notifyRenderer.add("公频");
                                                    onlineList.updateUI();
                                                }

                                                StyleConstants.setForeground(attrSet, generalTextColor);
                                                publicDoc.insertString(publicDoc.getLength(),
                                                        getTimeStamp() + analyzedMsgs[1] + ": " + analyzedMsgs[2] + "\n", attrSet);

                                                //遍历socket list群发
                                                for(Socket clientIter : clientList)
                                                {
                                                    ps = new PrintStream(clientIter.getOutputStream());
                                                    ps.println("public&" + analyzedMsgs[1] + "&" + analyzedMsgs[2]);
                                                }
                                            }
                                            else if (analyzedMsgs[0].equals("fileFlag"))
                                            {
                                                //TODO:传文件功能
                                            }
                                            else if (analyzedMsgs[0].equals("query"))        //转发私聊
                                            {
                                                ps = new PrintStream(nameSocketMap.get(analyzedMsgs[2]).getOutputStream());
                                                ps.println("query&" + analyzedMsgs[1] + "&" + analyzedMsgs[3]);
                                            }
                                            else if (analyzedMsgs[0].equals("toMonitor"))
                                            {
                                                if (!onlineList.getSelectedValue().toString().equals(analyzedMsgs[1]))      //给tab加高亮
                                                {
                                                    notifyRenderer.add(analyzedMsgs[1]);
                                                    onlineList.updateUI();
                                                }
                                                Document currentQueryDoc = nameDocMap.get(analyzedMsgs[1]);

                                                StyleConstants.setForeground(attrSet, generalTextColor);
                                                currentQueryDoc.insertString(currentQueryDoc.getLength(),
                                                        getTimeStamp() + analyzedMsgs[1] + ": " + analyzedMsgs[2] + "\n", attrSet);
                                            }
                                        }
                                    }
                                    catch(SocketException offlineException)         //有人下线
                                    {
                                        // 检索与清除
                                        clientList.remove(newClient);
                                        String offlineNickname = null;
                                        for(HashMap.Entry<String, Socket> nicknameIter : nameSocketMap.entrySet())      //由值反向求键，感觉很蛋疼
                                        {
                                            if (nicknameIter.getValue().equals(newClient))
                                            {
                                                offlineNickname = nicknameIter.getKey();
                                                break;
                                            }
                                        }

                                        // 离开私聊，如果在的话
                                        if (onlineList.getSelectedValue().toString().equals(offlineNickname))
                                        {
                                            onlineList.setSelectedIndex(0);
                                            logArea.setDocument(publicDoc);
                                        }
                                        onlineListModel.removeElement(offlineNickname);
                                        //onlineList.updateUI();
                                        nameSocketMap.remove(offlineNickname);
                                        nameDocMap.remove(offlineNickname);

                                        // 消息更新
                                        try
                                        {
                                            StyleConstants.setForeground(attrSet, activityTextColor);
                                            publicDoc.insertString(publicDoc.getLength(),
                                                    getTimeStamp() + "【" + offlineNickname + "】刚刚下线了\n", attrSet);
                                        }
                                        catch(BadLocationException impossible)
                                        {
                                            impossible.printStackTrace();
                                        }

                                        // 消息奔走相告到各个客户端
                                        try
                                        {
                                            for(Socket clientIter : clientList)
                                            {
                                                ps = new PrintStream(clientIter.getOutputStream());
                                                ps.println("offline&" + offlineNickname);
                                            }

                                        // 盖上棺材盖
                                            newClient.close();
                                        } catch(IOException must){}
                                    }
                                    catch(Exception ex)
                                    {
                                        ex.printStackTrace();
                                    }
                                }
                            }).start();
                            
                        }
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }).start();

        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
    //
    //  主方法
    //
    public static void main(String[] args)
    {
        new Server();                       //感觉有点用面向对象写面向过程的意味
    }
}
// Enjoy <3