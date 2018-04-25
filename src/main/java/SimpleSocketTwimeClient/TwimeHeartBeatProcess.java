package SimpleSocketTwimeClient;

import org.agrona.concurrent.UnsafeBuffer;
import sbe.MessageHeaderEncoder;
import sbe.SequenceEncoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * процесс отправки heartbeat через TWIME
 */
public class TwimeHeartBeatProcess implements Runnable{
    private static final Logger logger = Logger.getLogger(TwimeHeartBeatProcess.class.getName());

    private boolean isStopped = false;
    private WritableByteChannel channel = null;
    private long intervalMsec = 0;
    private byte[] bArray = new byte[4096];
    ByteBuffer byteBuffer = ByteBuffer.wrap(bArray);
    UnsafeBuffer directBuffer = new UnsafeBuffer(byteBuffer);
    private boolean isGenerated = false;
    private int encodingLength = 0;
    private AbstractTwimeClient abstractTwimeClient = null;

    MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    SequenceEncoder sequenceEncoder = new SequenceEncoder();

    public TwimeHeartBeatProcess(WritableByteChannel channel, long intervalMsec) {
        this.channel = channel;
        this.intervalMsec = intervalMsec;
    }

    public boolean isStopped() {
        return isStopped;
    }

    public TwimeHeartBeatProcess setStopped(boolean stopped) {
        isStopped = stopped;
        return this;
    }

    private void sendSequence(long seqNum){
        byteBuffer.clear();

        if ( !isGenerated ) {
            int bufferOffset = 0;
            encodingLength = 0;
            messageHeaderEncoder.wrap(directBuffer, bufferOffset)
                    .blockLength(sequenceEncoder.sbeBlockLength())
                    .templateId(sequenceEncoder.sbeTemplateId())
                    .schemaId(sequenceEncoder.sbeSchemaId())
                    .version(sequenceEncoder.sbeSchemaVersion());

            bufferOffset += messageHeaderEncoder.encodedLength();
            encodingLength += messageHeaderEncoder.encodedLength();

            sequenceEncoder.wrap(directBuffer, bufferOffset).nextSeqNo(SequenceEncoder.nextSeqNoNullValue());
            //sequenceEncoder.nextSeqNo(seqNum);
            encodingLength += sequenceEncoder.encodedLength();
            isGenerated = true;
        }

        byteBuffer.limit(encodingLength);
        try {
            logger.fine(" >> send sequence ...");
            channel.write(byteBuffer);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "IOException: ", e);
            this.setStopped(true);
        }
    }

    @Override
    public void run() {
        while(!isStopped){
            try {
                Thread.sleep(intervalMsec);
                if(!isStopped && needSendHeartbeat()) {
                    sendSequence(1);
                }
            } catch (InterruptedException e) {
                logger.log(Level.SEVERE, "InterruptedException: ", e);
                isStopped = true;
            }
        }
    }

    public AbstractTwimeClient getAbstractTwimeClient() {
        return abstractTwimeClient;
    }

    public TwimeHeartBeatProcess setAbstractTwimeClient(AbstractTwimeClient abstractTwimeClient) {
        this.abstractTwimeClient = abstractTwimeClient;
        return this;
    }

    private boolean needSendHeartbeat(){
        boolean result = true;
        if (abstractTwimeClient != null){
            if (java.sql.Timestamp.from(Instant.now()).getTime() - abstractTwimeClient.getLastSendTime() < intervalMsec) {
                result = false;
            }
        }
        return result;
    }
}