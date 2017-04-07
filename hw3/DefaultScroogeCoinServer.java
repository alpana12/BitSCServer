package hw3;

import hw3.HashPointer;
import hw3.Transaction;

import java.security.*;
import java.util.*;

/**
 * Created by alpana
 */

//Scrooge creates coins by adding outputs to a transaction to his public key.
//In ScroogeCoin, Scrooge can out as many coins as he wants.
//No one else can out a coin.
//A user owns a coin if a coin is transfer to him from its current owner
public class DefaultScroogeCoinServer implements ScroogeCoinServer {

	private KeyPair scroogeKeyPair;
	private ArrayList<Transaction> ledger = new ArrayList();

	//Set scrooge's key pair
	@Override
	public synchronized void init(KeyPair scrooge) {
		scroogeKeyPair=new KeyPair(scrooge.getPublic(),scrooge.getPrivate());
		
	}

	public synchronized List<HashPointer> epochHandler(List<Transaction> transList)  { //For every 10 minute epoch (hypothetical, did not set the time), 
		List<HashPointer> ptrList = new ArrayList<HashPointer>();
		while(!transList.isEmpty()){                  //this method is called with an unordered ptrList of proposed transactions
			List<Transaction> invalidTransList = new ArrayList<Transaction>();
			for(Transaction trx:transList){
				if(!isValid(trx)){
                                                            //Checking whether a transaction is valid or not
					invalidTransList.add(trx);
				}else {	
                                                                //Creating pointer to the transaction
					ledger.add(trx);
					HashPointer hp = new HashPointer(trx.getHash(),ledger.size()-1);
					ptrList.add(hp);
				}
			}
			if(transList.size()==invalidTransList.size()) break;
                        	//If the method does not accept an valid transaction, the user must try to submit the transaction
                          // again during the next epoch.
			transList = invalidTransList;
		}
		return ptrList; 	//Returns a ptrList of hash pointers to transactions accepted for this epoch
	}

	@Override
        	//Returns true if and only if transaction trx meets the following conditions:
	public synchronized boolean isValid(Transaction tran) { /// validity function : maximum work is done here.
		Transaction tr = tran;
		switch(tr.getType()){
			case Create: 	//CreateCoin transaction
				if(tr.numInputs()>0) return false;  	//	(1) no inputs
				for(Transaction.Output out : tr.getOutputs()){
					if(out.getValue()<=0) return false;//	(3) all of txâ€™s output values are positive
					if(out.getPublicKey()!=scroogeKeyPair.getPublic()) return false; 	//(2) all outputs are given to Scrooge's public key
				}
					
                                                                   //	(4) Scrooge's sig of the transaction is included - verified in try
				try {
					Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
					sig.initVerify(scroogeKeyPair.getPublic());
					sig.update(tran.getRawBytes());
					if (!sig.verify(tr.getSignature())) {
						return false;   //fails if sig verification fails
					}
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
				return true;
			case Pay:     	//PayCoin transaction
				Set<UTXO> utxo = getUTXOs();
				double inTotal = 0;
                                                     //get the inputs
				for(int i=0;i<tr.numInputs();i++){
					Transaction.Input in = tr.getInputs().get(i);
					int outIndex = in.getIndexOfTxOutput();
					int indexofledger = gtLdgrIndex(in.getHashOfOutputTx(),utxo,outIndex,in);
					if(indexofledger==-1) return false; //checking if two transactions have same hash
					Transaction.Output inout = ledger.get(indexofledger).getOutput(outIndex);
					inTotal+=inout.getValue(); // adding input values
					PublicKey pk = inout.getPublicKey();
					try { //verify for scrooge signature
						Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
						sig.initVerify(pk);
						sig.update(tr.getRawDataToSign(i));
						if (!sig.verify(in.getSignature())) {
							return false;
						}
					} catch (Exception ex) {
						throw new RuntimeException(ex);
					}
				}
                                                     //outputs
				double outTotal = 0;
				for(Transaction.Output op : tr.getOutputs()){
					if(op.getValue()<=0) return false;
					outTotal+=op.getValue();
				}
				if(Math.abs(inTotal-outTotal)<.000001){ 
					return true; 
				}else{
					return false;
				}
		}
		return false;
	}

	private int gtLdgrIndex(byte[] outTransHash, Set<UTXO> utxo, int outIndex, Transaction.Input in) {
		for(int j=0;j<ledger.size();j++){
			if(Arrays.equals(ledger.get(j).getHash(),outTransHash)){
				HashPointer hp = new HashPointer(in.getHashOfOutputTx(), j);
				UTXO inputUTXO = new UTXO(hp,outIndex);
				if(utxo.contains(inputUTXO)) {
					return j; //checks the utxo and returns the index
				}
			}
		}
		return -1;
	}
	//	(1) all inputs claimed by trx are in the current unspent (j.ex. in getUTOXs()),
	//	(2) the signatures on each input of trx are valid,
	//	(3) no UTXO is claimed multiple times by trx,
	//	(4) all of txâ€™s output values are positive, and
	//	(5) the sum of txâ€™s input values is equal to the sum of its output values;
	@Override
	public synchronized Set<UTXO> getUTXOs() {
            //getting all unspent transactions on to the ledger
		Set<UTXO> utxo = new HashSet<UTXO>(); //Entering all details on the ledger
		for(int lgIndex = 0;lgIndex<ledger.size();lgIndex++){
			Transaction tr = ledger.get(lgIndex);
			switch(tr.getType()){
				case Create:
					for(Transaction.Output out : tr.getOutputs()){
						int ind = tr.getIndex(out);
						HashPointer hp = new HashPointer(tr.getHash(),lgIndex);
						UTXO utxoNew = new UTXO(hp,ind);
						utxo.add(utxoNew); //when created, it is added to UTXO
					}
					break;
				case Pay:
					for(int i=0;i<tr.numInputs();i++){
						Transaction.Input in = tr.getInputs().get(i);
						int ind = in.getIndexOfTxOutput(); //output trans index
						HashPointer hp = new HashPointer(in.getHashOfOutputTx(), gtLdgrIndex(in.getHashOfOutputTx(),utxo,ind,in));
						//input hash pointer
						UTXO inUtxo = new UTXO(hp,ind);
						utxo.remove(inUtxo); //removing from utxo after pay transaction
					}
					for(Transaction.Output out: tr.getOutputs()){
						int index = tr.getIndex(out);
						HashPointer hp = new HashPointer(tr.getHash(),lgIndex);
                                                                                //output hash pointer
						UTXO outUtxo = new UTXO(hp,index);
						utxo.add(outUtxo);
					}
					break;
			}
			
		}
		return utxo;
	}

}
