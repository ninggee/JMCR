package edu.tamu.aser.rvinstrumentation;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

public class RVSharedAccessEventsMethodTransformer extends AdviceAdapter implements Opcodes {
    
    final static String CLASS_INTEGER = "java/lang/Integer";
    final static String CLASS_BOOLEAN = "java/lang/Boolean";
    final static String CLASS_CHAR = "java/lang/Character";
    final static String CLASS_SHORT = "java/lang/Short";
    final static String CLASS_BYTE = "java/lang/Byte";
    final static String CLASS_LONG = "java/lang/Long";
    final static String CLASS_FLOAT = "java/lang/Float";
    final static String CLASS_DOUBLE = "java/lang/Double";

    final static String METHOD_VALUEOF = "valueOf";
    final static String DESC_INTEGER_VALUEOF = "(I)Ljava/lang/Integer;";
    final static String DESC_BOOLEAN_VALUEOF = "(Z)Ljava/lang/Boolean;";
    final static String DESC_BYTE_VALUEOF = "(B)Ljava/lang/Byte;";
    final static String DESC_SHORT_VALUEOF = "(S)Ljava/lang/Short;";
    final static String DESC_CHAR_VALUEOF = "(C)Ljava/lang/Character;";
    final static String DESC_LONG_VALUEOF = "(J)Ljava/lang/Long;";
    final static String DESC_FLOAT_VALUEOF = "(F)Ljava/lang/Float;";
    final static String DESC_DOUBLE_VALUEOF = "(D)Ljava/lang/Double;";

    boolean isInit,isSynchronized,isStatic, possibleRunMethod;
    String className;
    String source;
    String methodName;
    String methodSignature;
    private int maxindex_cur;   //current max index of local variables
    private int line_cur;

	public RVSharedAccessEventsMethodTransformer(
	        MethodVisitor mv, 
	        String source, 
	        int access, 
	        String desc, 
	        String cname, 
	        String mname, 
	        String msignature,
	        boolean isInit, 
	        boolean isSynchronized, 
	        boolean isStatic,
	        boolean possibleRunMethod) {
            
	        super(Opcodes.ASM5, mv, access, mname, desc);


            this.source = source == null ? "Unknown" : source;
            this.className = cname;
            this.methodName = mname;
            this.methodSignature = msignature;
            this.isInit = isInit;
            this.isSynchronized = isSynchronized;
            this.isStatic = isStatic;
            this.possibleRunMethod = possibleRunMethod;

            this.maxindex_cur = Type.getArgumentsAndReturnSizes(desc) + 1;

	}



    public void visitMaxs(int maxStack, int maxLocals) {
        mv.visitMaxs(maxStack + 5, maxindex_cur+2);//may change to ...

    }

    public void visitLineNumber(int line, Label start) {
        line_cur = line;
        mv.visitLineNumber(line, start);
    }
	    
    //对线程相关的各种方法进行插桩
    public void visitMethodInsn(int opcode, String owner, String name, String desc) {

        String sig_loc = source + "|" + (className+"|"+methodSignature+"|"+line_cur).replace("/", ".");
//        System.out.println(sig_loc);
        int ID  = RVGlobalStateForInstrumentation.instance.getLocationId(sig_loc);

        switch (opcode) {
        case INVOKEVIRTUAL:
            if(RVGlobalStateForInstrumentation.instance.isThreadClass(owner))
            {
                if(name.equals("start") &&desc.equals("()V")) {
                    
                    /*
                     * when a sync happens, we should do memBar operations
                     * 
                     */
                    if(RVConfig.instance.mode == "TSO" || RVConfig.instance.mode == "PSO"){
                        mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, 
                                RVConfig.instance.MEM_BARRIER, 
                                RVConfig.instance.DESC_MEM_BARRIER);
                    }
                    /* end */
                    
                    maxindex_cur++;
                    int index = maxindex_cur;
                    mv.visitInsn(DUP);
                    mv.visitVarInsn(ASTORE, index);
                    addBipushInsn(mv,ID);
                    mv.visitVarInsn(ALOAD, index);
                    
                   
                    
                    mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                            RVConfig.instance.LOG_THREAD_BEFORE_START,
                            RVConfig.instance.DESC_LOG_THREAD_START);

                    mv.visitMethodInsn(opcode, owner, name, desc);
                }
                else if(name.equals("join") &&desc.equals("()V")) {
          
                    maxindex_cur++;
                    int index = maxindex_cur;
                    //mv.visitInsn(DUP);
                    mv.visitVarInsn(ASTORE, index);

                    //mv.visitMethodInsn(opcode, owner, name, desc);

                    addBipushInsn(mv,ID);
                    mv.visitVarInsn(ALOAD, index);
                    mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                            RVConfig.instance.LOG_THREAD_JOIN,
                            RVConfig.instance.DESC_LOG_THREAD_JOIN);

                }
                
                else
                {                     
                    mv.visitMethodInsn(opcode, owner, name, desc);
                }
            }
            else
            {
                if(name.equals("wait") &&desc.equals("()V")) {

                maxindex_cur++;
                int index = maxindex_cur;
                //mv.visitInsn(DUP);
                mv.visitVarInsn(ASTORE, index);

                addBipushInsn(mv,ID);
                mv.visitVarInsn(ALOAD, index);
                mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                        RVConfig.instance.LOG_WAIT,
                        RVConfig.instance.DESC_LOG_WAIT);

                    //mv.visitMethodInsn(opcode, owner, name, desc);

                 }
                 else if (name.equals("notify") && desc.equals("()V")) {
                    maxindex_cur++;
                    int index = maxindex_cur;
                    // mv.visitInsn(DUP);
                    mv.visitVarInsn(ASTORE, index);

                    addBipushInsn(mv, ID);
                    mv.visitVarInsn(ALOAD, index);
                    mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                            RVConfig.instance.LOG_NOTIFY,
                            RVConfig.instance.DESC_LOG_NOTIFY);

                        // mv.visitMethodInsn(opcode, owner, name, desc);
                 }
                else if (name.equals("notifyAll") && desc.equals("()V")) {
                    maxindex_cur++;
                    int index = maxindex_cur;
                    // mv.visitInsn(DUP);
                    mv.visitVarInsn(ASTORE, index);

                    addBipushInsn(mv, ID);
                    mv.visitVarInsn(ALOAD, index);
                    mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                            RVConfig.instance.LOG_NOTIFY_ALL,
                            RVConfig.instance.DESC_LOG_NOTIFY);

                }
                else if (name.equals("lock") && desc.equals("()V")) {
                    //@ Alan: for handling reentranlock
                    maxindex_cur++;
                    int index = maxindex_cur;
                    mv.visitVarInsn(ASTORE, index);// objectref
                    // Comment out because of MCR lock
                    // mv.visitInsn(opcode);
                    addBipushInsn(mv, ID);
                    mv.visitVarInsn(ALOAD, index);

                    mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                            RVConfig.instance.LOG_LOCK_INSTANCE,
                            RVConfig.instance.DESC_LOG_LOCK_INSTANCE);
                }
                else if (name.equals("unlock") && desc.equals("()V")) {
                    //@Alan: for handling reentranlock
                    maxindex_cur++;
                    int index = maxindex_cur;
                    mv.visitVarInsn(ASTORE, index);// objectref
                    addBipushInsn(mv, ID);
                    mv.visitVarInsn(ALOAD, index);


                    mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                            RVConfig.instance.LOG_UNLOCK_INSTANCE,
                            RVConfig.instance.DESC_LOG_UNLOCK_INSTANCE);
                }
                else if (name.equals("lockInterruptibly") && desc.equals("()V")) {
                    //@ Alan: for handling reentranlock
                    maxindex_cur++;
                    int index = maxindex_cur;
                    mv.visitVarInsn(ASTORE, index);// objectref
                    // Comment out because of MCR lock
                    // mv.visitInsn(opcode);
                    addBipushInsn(mv, ID);
                    mv.visitVarInsn(ALOAD, index);

                    mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                            RVConfig.instance.LOG_LOCK_INSTANCE,
                            RVConfig.instance.DESC_LOG_LOCK_INSTANCE);
                }
    	         else
    	             mv.visitMethodInsn(opcode, owner, name, desc);
            }
             break;
             
        case INVOKESTATIC:
            if ((RVGlobalStateForInstrumentation.instance.isThreadClass(owner))
                &&name.equals("sleep") && (desc.equals(("(J)V")) || desc.equals("(JI)V"))) {
               mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                       RVConfig.instance.LOG_THREAD_SLEEP,
                       RVConfig.instance.DESC_LOG_THREAD_SLEEP);
            }
            
        case INVOKESPECIAL:

        case INVOKEINTERFACE:
            if (name.equals("lock") && desc.equals("()V")) {
                //@ Alan: for handling reentranlock
                maxindex_cur++;
                int index = maxindex_cur;
                mv.visitVarInsn(ASTORE, index);// objectref
                // Comment out because of MCR lock
                // mv.visitInsn(opcode);
                addBipushInsn(mv, ID);
                mv.visitVarInsn(ALOAD, index);

                mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                        RVConfig.instance.LOG_LOCK_INSTANCE,
                        RVConfig.instance.DESC_LOG_LOCK_INSTANCE);
            }
            else if (name.equals("unlock") && desc.equals("()V")) {
                //@Alan: for handling reentranlock
                maxindex_cur++;
                int index = maxindex_cur;
                mv.visitVarInsn(ASTORE, index);// objectref
                addBipushInsn(mv, ID);
                mv.visitVarInsn(ALOAD, index);


                mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                        RVConfig.instance.LOG_UNLOCK_INSTANCE,
                        RVConfig.instance.DESC_LOG_UNLOCK_INSTANCE);
            }
            else {
                mv.visitMethodInsn(opcode, owner, name, desc);
            }           
            break;
        default:
            System.err.println("Unknown method invocation opcode "+opcode);
            System.exit(1);
        }

    } //end visitMethodInsn
    
    private static final String AFTER_FIELD_ACCESS = "afterFieldAccess";
    private static final String BEFORE_FIELD_ACCESS = "beforeFieldAccess";
    private static final String AFTER_ARRAY_ACCESS = "afterArrayAccess";
    private static final String BEFORE_ARRAY_ACCESS = "beforeArrayAccess";
    private static final String OBJECT_VOID = "(Ljava/lang/Object;)V";
    private static final String BOOL_3STRINGS_VOID = "(ZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V";
    private static final String INT_OBJECT_INT_BOOLEAN_VOID = "(ILjava/lang/Object;IZ)V";
    private static final String BOOL_VOID = "(Z)V";

    /**
     * Inform the scheduler that it is to visit a field, wait for the choice of the scheduler
     * @param isBefore
     * @param isRead
     * @param fieldOwner
     * @param fieldName
     * @param fieldDesc
     */
    private void informSchedulerAboutFieldAccess(boolean isBefore, boolean isRead, String fieldOwner, String fieldName, String fieldDesc) {
        super.mv.visitInsn(isRead ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        super.mv.visitLdcInsn(fieldOwner);
        super.mv.visitLdcInsn(fieldName);
        super.mv.visitLdcInsn(fieldDesc);
        if (isBefore) {
            super.mv.visitMethodInsn(Opcodes.INVOKESTATIC, RVInstrumentor.INSTR_EVENTS_RECEIVER, BEFORE_FIELD_ACCESS, BOOL_3STRINGS_VOID);
        } else {
            super.mv.visitMethodInsn(Opcodes.INVOKESTATIC, RVInstrumentor.INSTR_EVENTS_RECEIVER, AFTER_FIELD_ACCESS, BOOL_3STRINGS_VOID);
        }
    }
    
    /**
     * 通知scheduler关于数组的访问信息
     * @param isBefore
     * @param isRead
     */
    private void informSchedulerAboutArrayAccess(boolean isBefore, boolean isRead) {
        super.mv.visitInsn(isRead ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        if (isBefore) {
            super.mv.visitMethodInsn(Opcodes.INVOKESTATIC, RVInstrumentor.INSTR_EVENTS_RECEIVER, BEFORE_ARRAY_ACCESS, BOOL_VOID);
        } else {
            super.mv.visitMethodInsn(Opcodes.INVOKESTATIC, RVInstrumentor.INSTR_EVENTS_RECEIVER, AFTER_ARRAY_ACCESS, BOOL_VOID);
        }
    }
    
    /**
     * visit field instruction
     */
	public void visitFieldInsn(int opcode, String owner, String name, String desc) {
	    
	    String sig_var = (owner + "." + name).replace("/", ".");// + "_" + opcode;		
	    /*
	     * if using this, for x=x, read(x) and write(x) have the same signature
	     * but it's OK because it it not difficult to check the source code
	     */
	    String sig_loc = (owner + "|" + methodSignature + "|" + sig_var + "|" + line_cur).replace("/", ".");
//	    String sig_loc = ( sig_var + "|" + line_cur ).replace("/", ".");  //the above is too verbose
	    int SID = RVGlobalStateForInstrumentation.instance.getVariableId(sig_var);   //SID : sig_var //variable
	    int ID = RVGlobalStateForInstrumentation.instance.getLocationId(sig_loc);    //ID : sig_loc   //location

	    boolean isRead = false;
        if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
            isRead = true;
        }

	 
		switch (opcode) {
		case GETSTATIC:	    
		    if (!isInit){
		        this.informSchedulerAboutFieldAccess(true, isRead, owner, name, desc);
		    }
		    
		    /*
		     * Before execute the read operation, it needs to empty the buffer first
		     * otherwise a read might happen before a write to the same addr
		     * e.g. w(x) w(y) r(z) r(y)
		     */
		    
		    if(RVConfig.instance.mode == "TSO" || RVConfig.instance.mode == "PSO"){
		        mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, 
                        RVConfig.instance.MEM_BARRIER, 
                        RVConfig.instance.DESC_MEM_BARRIER);
		    }
		    	    
		    mv.visitFieldInsn(opcode, owner, name, desc);
			if (!isInit) {
				maxindex_cur++;

				int index = maxindex_cur;

				// store the value already read
				storeValue(desc, index);

				//prepare for the parameters of the inserted method
				addBipushInsn(mv, ID);
				mv.visitInsn(ACONST_NULL);
				addBipushInsn(mv, SID);
				loadValue(desc, index);
				addBipushInsn(mv, 0);

				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
						RVConfig.instance.LOG_FIELD_ACCESS,
						RVConfig.instance.DESC_LOG_FIELD_ACCESS);
				
				//judge whether or not to do an updating operation @Alan
				if(RVConfig.instance.mode=="TSO"){
    	            mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, 
    	                    RVConfig.instance.UPDATE_STORE, 
    	                    RVConfig.instance.DESC_UPDATE_STORE);

				}
				else if (RVConfig.instance.mode=="PSO") {
				    mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, 
                            RVConfig.instance.UPDATE_STORE_PSO, 
                            RVConfig.instance.DESC_UPDATE_STORE_PSO);
                }
			}
			break;

		/*
		 * for write operations, store them into buffer instead of executing them
		 */
		case PUTSTATIC:
			maxindex_cur++;
			int index = maxindex_cur;
			
			// store the value to be written
			storeValue(desc, index);
			//mv.visitVarInsn(ISTORE, index);
            this.informSchedulerAboutFieldAccess(true, isRead, owner, name, desc); //call beforeFieldAccess
			  
            if(isInit){
                putStatic(desc, owner, name, opcode, ID, SID, index);
            }
            else{
                if(RVConfig.instance.mode== "TSO" || RVConfig.instance.mode== "PSO"){
                   
                    /*
                     * it is important to pop the value in the stack
                     * when return it requires stakc{  }
                     */
                    
                    //this is error prone
                    //mv.visitVarInsn(ISTORE, index);  //pop value to #index
                    popValue(desc, index);
                    
                    String info =  owner + ":" + name + ":" + ID + ":" + SID;
                    //the parameter
                    loadValue(desc, index);  //the value
                    mv.visitLdcInsn(info);
                    /*
                     * instrument the buffer operation 
                     * this callback function will return a boolean
                     * to decide whether or not it needs to be buffered
                     */
                    if (RVConfig.instance.mode=="TSO"){
                        mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, 
                                RVConfig.instance.BUFFER_STORE, 
                                RVConfig.instance.DESC_BUFFER_STORE);
                    }
                    else{
                        mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, 
                                RVConfig.instance.BUFFER_STORE_PSO, 
                                RVConfig.instance.DESC_BUFFER_STORE_PSO);
                    }           
                }
                else{
                    putStatic(desc, owner, name, opcode, ID, SID, index);
                }
            }
           
			break;

		case GETFIELD:
			if (!isInit) {
				maxindex_cur++;
				int index1 = maxindex_cur;
				mv.visitInsn(DUP);
				mv.visitVarInsn(ASTORE, index1);
	            this.informSchedulerAboutFieldAccess(true, isRead, owner, name, desc);
				mv.visitFieldInsn(opcode, owner, name, desc);

				maxindex_cur++;
				int index2 = maxindex_cur;
				// store the value already read
				storeValue(desc, index2);

				addBipushInsn(mv, ID);
				mv.visitVarInsn(ALOAD, index1);
				addBipushInsn(mv, SID);
				loadValue(desc, index2);

				addBipushInsn(mv, 0);

				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
						RVConfig.instance.LOG_FIELD_ACCESS,
						RVConfig.instance.DESC_LOG_FIELD_ACCESS);
			} else {
				mv.visitFieldInsn(opcode, owner, name, desc);
			}
			break;

		case PUTFIELD:
			if (name.startsWith("this$")
					|| (className.contains("$") && name.startsWith("val$"))) {
				// inner class or strange class
				mv.visitFieldInsn(opcode, owner, name, desc);
				break;
			}

			maxindex_cur++;
			int index1 = maxindex_cur;
			int index2;
			if (desc.startsWith("D")) {
				mv.visitVarInsn(DSTORE, index1);
				maxindex_cur++;// double
				maxindex_cur++;
				index2 = maxindex_cur;
				mv.visitInsn(DUP);
				mv.visitVarInsn(ASTORE, index2);
				mv.visitVarInsn(DLOAD, index1);
			} else if (desc.startsWith("J")) {
				mv.visitVarInsn(LSTORE, index1);
				maxindex_cur++;// long
				maxindex_cur++;
				index2 = maxindex_cur;
				mv.visitInsn(DUP);
				mv.visitVarInsn(ASTORE, index2);
				mv.visitVarInsn(LLOAD, index1);
			} else if (desc.startsWith("F")) {
				mv.visitVarInsn(FSTORE, index1);
				maxindex_cur++;// float
				index2 = maxindex_cur;
				mv.visitInsn(DUP);
				mv.visitVarInsn(ASTORE, index2);
				mv.visitVarInsn(FLOAD, index1);
			} else if (desc.startsWith("[")) {
				mv.visitVarInsn(ASTORE, index1);
				maxindex_cur++;// ref or array
				index2 = maxindex_cur;
				mv.visitInsn(DUP);
				mv.visitVarInsn(ASTORE, index2);
				mv.visitVarInsn(ALOAD, index1);
			} else if (desc.startsWith("L")) {
				mv.visitVarInsn(ASTORE, index1);
				maxindex_cur++;// ref or array
				index2 = maxindex_cur;
				mv.visitInsn(DUP);
				mv.visitVarInsn(ASTORE, index2);
				mv.visitVarInsn(ALOAD, index1);
			} else {
				// integer,char,short,boolean
				mv.visitVarInsn(ISTORE, index1);
				maxindex_cur++;
				index2 = maxindex_cur;
				mv.visitInsn(DUP);
				mv.visitVarInsn(ASTORE, index2);
				mv.visitVarInsn(ILOAD, index1);
			}
			if (!isInit)
            this.informSchedulerAboutFieldAccess(true, isRead, owner, name, desc);

            mv.visitFieldInsn(opcode, owner, name, desc);

            addBipushInsn(mv, ID);
            mv.visitVarInsn(ALOAD, index2);
            addBipushInsn(mv, SID);
            loadValue(desc, index1);

            if (isInit) {
                mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                        RVConfig.instance.LOG_INIT_WRITE_ACCESS,
                        RVConfig.instance.DESC_LOG_INIT_WRITE_ACCESS);//
            } else {
                addBipushInsn(mv, 1);
                mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                        RVConfig.instance.LOG_FIELD_ACCESS,
                        RVConfig.instance.DESC_LOG_FIELD_ACCESS);
            }
			break;
		default:
			System.err.println("Unknown field access opcode " + opcode);
			System.exit(1);
		}
	} // end visitFieldInsn

	/**
	 * called in the case: initially put static 
	 */
	public void putStatic(String desc, String owner, String name, int opcode, int ID, int SID, int index)
	{
	    mv.visitFieldInsn(opcode, owner, name, desc);  // putstatic     
        logFieldAccess(desc, ID, SID, index);       
	}	
	
	/**
	 * log field access
	 */
	public void logFieldAccess(String desc, int ID, int SID, int index){
	    addBipushInsn(mv, ID);   
        mv.visitInsn(ACONST_NULL);
        addBipushInsn(mv, SID);
        loadValue(desc, index); //load the value wrote to the field, value go into the stack

        /*
         * invoke the fieldlog
         */
        if (isInit) {
            mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                    RVConfig.instance.LOG_INIT_WRITE_ACCESS,
                    RVConfig.instance.DESC_LOG_INIT_WRITE_ACCESS);//
        } else {
            addBipushInsn(mv, 1);
            mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                    RVConfig.instance.LOG_FIELD_ACCESS,
                    RVConfig.instance.DESC_LOG_FIELD_ACCESS);//
        }
	}

	/**
	 * visit a local variable instruction
	 * @param opcode the opcode of the local variable instruction to be visited. This opcode is either ILOAD, LLOAD, FLOAD, DLOAD, ALOAD, ISTORE, LSTORE, FSTORE, DSTORE, ASTORE or RET.
	 * @param var the operand of the instruction to be visited. This operand is the index of a local variable.
	 */
	@Override
	public void visitVarInsn(int opcode, int var) {
	    // 判断要访问的局部变量的index是不是大于maxindex_cur, 如果是的话，就更新maxindex_cur
		if (var > maxindex_cur) {
			maxindex_cur = var;
		}

		switch (opcode) {
		//如果是load或者store long或者double类型的局部变量，就把maxindex_cur的值加一，可能是由于long和double的长度都是8个byte，其他类型都是4个byte
		case LSTORE:
		case DSTORE:
		case LLOAD:
		case DLOAD:
			if (var == maxindex_cur) {
				maxindex_cur = var + 1;
			}
			mv.visitVarInsn(opcode, var);
			break;
		case ISTORE:
		case FSTORE:
		case ASTORE:
		case ILOAD:
		case FLOAD:
		case ALOAD:
		case RET:
			mv.visitVarInsn(opcode, var);
			break;
		default:
			System.err.println("Unknown var instruction opcode " + opcode);
			System.exit(1);
		}
	}

	/**
	 * visit an IINC instruction
	 * @param var the index of local variable to be incremented
	 * @param increment amount to increment the local variable by
	 */
	@Override
	public void visitIincInsn(int var, int increment) {
		if (var > maxindex_cur) {
			maxindex_cur = var;
		}
		mv.visitIincInsn(var, increment);
	}

	public void visitInsn(int opcode) {
		// System.out.println("visit insn!");
        boolean arrayRead = false;
        boolean arrayWrite = false;

		String sig_loc = (className + "|" + methodSignature + "|" + line_cur).replace("/", ".");
        int ID = RVGlobalStateForInstrumentation.instance.getLocationId(sig_loc);

        if (opcode == Opcodes.AALOAD || opcode == Opcodes.IALOAD || opcode == Opcodes.LALOAD
                || opcode == Opcodes.SALOAD || opcode == Opcodes.CALOAD || opcode == Opcodes.DALOAD
                || opcode == Opcodes.FALOAD || opcode == Opcodes.BALOAD) {
            arrayRead = true;


        } else if (opcode == Opcodes.AASTORE || opcode == Opcodes.IASTORE || opcode == Opcodes.LASTORE
                || opcode == Opcodes.SASTORE || opcode == Opcodes.CASTORE || opcode == Opcodes.DASTORE
                || opcode == Opcodes.FASTORE || opcode == Opcodes.BASTORE) {
            arrayWrite = true;

        }
        boolean arrayAccess = (arrayRead || arrayWrite);

        // 如果是数组的访问，要特殊对待
		if(arrayAccess) 
		{ 
    		switch (opcode) {
    		case AALOAD:
    			if (!isInit) {
    			    //复制栈顶2个数值，并压入栈顶
    				mv.visitInsn(DUP2);
    				maxindex_cur++;
    				int index1 = maxindex_cur;
    				mv.visitVarInsn(ISTORE, index1);
    				maxindex_cur++;
    				int index2 = maxindex_cur;
    				mv.visitVarInsn(ASTORE, index2);
                    this.informSchedulerAboutArrayAccess(true, arrayRead);
    				mv.visitInsn(opcode);
    				mv.visitInsn(DUP);
    				maxindex_cur++;
    				int index3 = maxindex_cur;
    				mv.visitVarInsn(ASTORE, index3);

    				addBipushInsn(mv, ID);
    				mv.visitVarInsn(ALOAD, index2);
    				mv.visitVarInsn(ILOAD, index1);
    				mv.visitVarInsn(ALOAD, index3);

    				addBipushInsn(mv, 0);

    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_ARRAY_ACCESS,
    						RVConfig.instance.DESC_LOG_ARRAY_ACCESS);
    			} else
    				mv.visitInsn(opcode);

    			break;

    		case BALOAD:
    		case CALOAD:
    		case SALOAD:
    		case IALOAD:
    			if (!isInit) {
    				mv.visitInsn(DUP2);
    				maxindex_cur++;
    				int index1 = maxindex_cur;
    				mv.visitVarInsn(ISTORE, index1);
    				maxindex_cur++;
    				int index2 = maxindex_cur;
    				mv.visitVarInsn(ASTORE, index2);
                    this.informSchedulerAboutArrayAccess(true, arrayRead);

    				mv.visitInsn(opcode);
    				mv.visitInsn(DUP);
    				maxindex_cur++;
    				int index3 = maxindex_cur;
    				mv.visitVarInsn(ISTORE, index3);

    				addBipushInsn(mv, ID);
    				mv.visitVarInsn(ALOAD, index2);
    				mv.visitVarInsn(ILOAD, index1);
    				mv.visitVarInsn(ILOAD, index3);

    				convertPrimitiveToObject(opcode);

    				addBipushInsn(mv, 0);

    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_ARRAY_ACCESS,
    						RVConfig.instance.DESC_LOG_ARRAY_ACCESS);
    			} else
    				mv.visitInsn(opcode);
    			break;
    		case FALOAD:
    			if (!isInit) {
    				mv.visitInsn(DUP2);
    				maxindex_cur++;
    				int index1 = maxindex_cur;
    				mv.visitVarInsn(ISTORE, index1); // array index
    				maxindex_cur++;
    				int index2 = maxindex_cur;
    				mv.visitVarInsn(ASTORE, index2); // index of the array in local parameter tabel
                    this.informSchedulerAboutArrayAccess(true, arrayRead);

    				mv.visitInsn(opcode);
    				mv.visitInsn(DUP);
    				maxindex_cur++;
    				int index3 = maxindex_cur;
    				mv.visitVarInsn(FSTORE, index3); // the float value get from the array 

    				addBipushInsn(mv, ID);
    				mv.visitVarInsn(ALOAD, index2);
    				mv.visitVarInsn(ILOAD, index1);
    				mv.visitVarInsn(FLOAD, index3);

    				convertPrimitiveToObject(opcode);

    				addBipushInsn(mv, 0);

    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_ARRAY_ACCESS,
    						RVConfig.instance.DESC_LOG_ARRAY_ACCESS);
    			} else
    				mv.visitInsn(opcode);

    			break;
    		case DALOAD:
    			if (!isInit) {
    				mv.visitInsn(DUP2);
    				maxindex_cur++;
    				int index1 = maxindex_cur;
    				mv.visitVarInsn(ISTORE, index1);
    				maxindex_cur++;
    				int index2 = maxindex_cur;
    				mv.visitVarInsn(ASTORE, index2);
                    this.informSchedulerAboutArrayAccess(true, arrayRead);

    				mv.visitInsn(opcode);
    				mv.visitInsn(DUP2);// double
    				maxindex_cur++;
    				int index3 = maxindex_cur;
    				mv.visitVarInsn(DSTORE, index3);
    				maxindex_cur++;

    				addBipushInsn(mv, ID);
    				mv.visitVarInsn(ALOAD, index2);
    				mv.visitVarInsn(ILOAD, index1);
    				mv.visitVarInsn(DLOAD, index3);

    				convertPrimitiveToObject(opcode);

    				addBipushInsn(mv, 0);

    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_ARRAY_ACCESS,
    						RVConfig.instance.DESC_LOG_ARRAY_ACCESS);
    			} else
    				mv.visitInsn(opcode);
    			break;
    		case LALOAD:
    			if (!isInit) {
    				mv.visitInsn(DUP2);
    				maxindex_cur++;
    				int index1 = maxindex_cur;
    				mv.visitVarInsn(ISTORE, index1);
    				maxindex_cur++;
    				int index2 = maxindex_cur;
    				mv.visitVarInsn(ASTORE, index2);
                    this.informSchedulerAboutArrayAccess(true, arrayRead);
                    mv.visitInsn(opcode);
    				mv.visitInsn(DUP2);// long
    				maxindex_cur++;
    				int index3 = maxindex_cur;
    				mv.visitVarInsn(LSTORE, index3);
    				maxindex_cur++;

    				addBipushInsn(mv, ID);
    				mv.visitVarInsn(ALOAD, index2);
    				mv.visitVarInsn(ILOAD, index1);
    				mv.visitVarInsn(LLOAD, index3);

    				convertPrimitiveToObject(opcode);

    				addBipushInsn(mv, 0);

    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_ARRAY_ACCESS,
    						RVConfig.instance.DESC_LOG_ARRAY_ACCESS);
    			} else
    				mv.visitInsn(opcode);
    			break;
    		case AASTORE:
    			maxindex_cur++;
    			int index1 = maxindex_cur;
    			mv.visitVarInsn(ASTORE, index1);
    			maxindex_cur++;
    			int index2 = maxindex_cur;
    			mv.visitVarInsn(ISTORE, index2);

    			mv.visitInsn(DUP);
    			maxindex_cur++;
    			int index3 = maxindex_cur;
    			mv.visitVarInsn(ASTORE, index3);// arrayref
    			mv.visitVarInsn(ILOAD, index2);// index
    			mv.visitVarInsn(ALOAD, index1);// value
                this.informSchedulerAboutArrayAccess(true, arrayRead);

    			mv.visitInsn(opcode);

    			addBipushInsn(mv, ID);
    			mv.visitVarInsn(ALOAD, index3);
    			mv.visitVarInsn(ILOAD, index2);
    			mv.visitVarInsn(ALOAD, index1);

    			if (isInit) {
    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_INIT_WRITE_ACCESS,
    						RVConfig.instance.DESC_LOG_INIT_WRITE_ACCESS);

    			} else {
    				addBipushInsn(mv, 1);
    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_ARRAY_ACCESS,
    						RVConfig.instance.DESC_LOG_ARRAY_ACCESS);
    			}
    			break;
    		case BASTORE:
    		case CASTORE:
    		case SASTORE:
    		case IASTORE:
    			maxindex_cur++;
    			index1 = maxindex_cur;
    			mv.visitVarInsn(ISTORE, index1);
    			maxindex_cur++;
    			index2 = maxindex_cur;
    			mv.visitVarInsn(ISTORE, index2);

    			mv.visitInsn(DUP);
    			maxindex_cur++;
    			index3 = maxindex_cur;
    			mv.visitVarInsn(ASTORE, index3);// arrayref
    			mv.visitVarInsn(ILOAD, index2);// index
    			mv.visitVarInsn(ILOAD, index1);// value
                this.informSchedulerAboutArrayAccess(true, arrayRead);

    			mv.visitInsn(opcode);

    			addBipushInsn(mv, ID);
    			mv.visitVarInsn(ALOAD, index3);
    			mv.visitVarInsn(ILOAD, index2);
    			mv.visitVarInsn(ILOAD, index1);
    			convertPrimitiveToObject(opcode);

    			if (isInit) {
    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_INIT_WRITE_ACCESS,
    						RVConfig.instance.DESC_LOG_INIT_WRITE_ACCESS);

    			} else {
    				addBipushInsn(mv, 1);
    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_ARRAY_ACCESS,
    						RVConfig.instance.DESC_LOG_ARRAY_ACCESS);
    			}

    			break;
    		case FASTORE:
    			maxindex_cur++;
    			index1 = maxindex_cur;
    			mv.visitVarInsn(FSTORE, index1);
    			maxindex_cur++;
    			index2 = maxindex_cur;
    			mv.visitVarInsn(ISTORE, index2);

    			mv.visitInsn(DUP);
    			maxindex_cur++;
    			index3 = maxindex_cur;
    			mv.visitVarInsn(ASTORE, index3);// arrayref
    			mv.visitVarInsn(ILOAD, index2);// index
    			mv.visitVarInsn(FLOAD, index1);// value
                this.informSchedulerAboutArrayAccess(true, arrayRead);

    			mv.visitInsn(opcode);

    			addBipushInsn(mv, ID);
    			mv.visitVarInsn(ALOAD, index3);
    			mv.visitVarInsn(ILOAD, index2);
    			mv.visitVarInsn(FLOAD, index1);
    			convertPrimitiveToObject(opcode);

    			if (isInit) {
    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_INIT_WRITE_ACCESS,
    						RVConfig.instance.DESC_LOG_INIT_WRITE_ACCESS);

    			} else {
    				addBipushInsn(mv, 1);
    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_ARRAY_ACCESS,
    						RVConfig.instance.DESC_LOG_ARRAY_ACCESS);
    			}
    			break;
    		case DASTORE:
    			maxindex_cur++;
    			index1 = maxindex_cur;
    			mv.visitVarInsn(DSTORE, index1);
    			maxindex_cur++;
    			mv.visitInsn(DUP2);// dup arrayref and index
    			maxindex_cur++;
    			index2 = maxindex_cur;
    			mv.visitVarInsn(ISTORE, index2);// index
    			maxindex_cur++;
    			index3 = maxindex_cur;
    			mv.visitVarInsn(ASTORE, index3);// arrayref

    			mv.visitVarInsn(DLOAD, index1);// double value

                this.informSchedulerAboutArrayAccess(true, arrayRead);
                mv.visitInsn(opcode);

    			addBipushInsn(mv, ID);
    			mv.visitVarInsn(ALOAD, index3);
    			mv.visitVarInsn(ILOAD, index2);
    			mv.visitVarInsn(DLOAD, index1);
    			convertPrimitiveToObject(opcode);

    			if (isInit) {
    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_INIT_WRITE_ACCESS,
    						RVConfig.instance.DESC_LOG_INIT_WRITE_ACCESS);

    			} else {
    				addBipushInsn(mv, 1);
    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_ARRAY_ACCESS,
    						RVConfig.instance.DESC_LOG_ARRAY_ACCESS);
    			}
    			break;
    		case LASTORE:
    			maxindex_cur++;
    			index1 = maxindex_cur;
    			mv.visitVarInsn(LSTORE, index1);
    			maxindex_cur++;
    			mv.visitInsn(DUP2);// dup arrayref and index
    			maxindex_cur++;
    			index2 = maxindex_cur;
    			mv.visitVarInsn(ISTORE, index2);// index
    			maxindex_cur++;
    			index3 = maxindex_cur;
    			mv.visitVarInsn(ASTORE, index3);// arrayref

    			mv.visitVarInsn(LLOAD, index1);// double value

                this.informSchedulerAboutArrayAccess(true, arrayRead);
                mv.visitInsn(opcode);

    			addBipushInsn(mv, ID);
    			mv.visitVarInsn(ALOAD, index3);
    			mv.visitVarInsn(ILOAD, index2);
    			mv.visitVarInsn(LLOAD, index1);
    			convertPrimitiveToObject(opcode);

    			if (isInit) {
    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_INIT_WRITE_ACCESS,
    						RVConfig.instance.DESC_LOG_INIT_WRITE_ACCESS);

    			} else {
    				addBipushInsn(mv, 1);
    				mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
    						RVConfig.instance.LOG_ARRAY_ACCESS,
    						RVConfig.instance.DESC_LOG_ARRAY_ACCESS);
    			}
    			break;
    		default:
    			mv.visitInsn(opcode);
    			break;
    		}
		}
		else
		{
		   //如果不是对数组的操作
		    switch (opcode) {
                case MONITORENTER:
                    //如果是进入临界区的操作
                    
                    /*
                     * membar
                     */
                   if(RVConfig.instance.mode == "TSO" || RVConfig.instance.mode == "PSO"){
                        mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, 
                                RVConfig.instance.MEM_BARRIER, 
                                RVConfig.instance.DESC_MEM_BARRIER);
                    }
                    //mv.visitInsn(DUP);
                    maxindex_cur++;
                    int index = maxindex_cur;
                    mv.visitVarInsn(ASTORE, index);// objectref 被锁的对象
                    // Comment out because of MCR lock
                    // mv.visitInsn(opcode);
                    addBipushInsn(mv, ID);
                    mv.visitVarInsn(ALOAD, index);

                    mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                            RVConfig.instance.LOG_LOCK_INSTANCE,
                            RVConfig.instance.DESC_LOG_LOCK_INSTANCE);
                    break;
                case MONITOREXIT:
                    
                    /*
                     * membar
                     */
                   if(RVConfig.instance.mode == "TSO" || RVConfig.instance.mode == "PSO"){
                        mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, 
                                RVConfig.instance.MEM_BARRIER, 
                                RVConfig.instance.DESC_MEM_BARRIER);
                    }
                    
                    //mv.visitInsn(DUP);
                    maxindex_cur++;
                    index = maxindex_cur;
                    mv.visitVarInsn(ASTORE, index);// objectref
                    addBipushInsn(mv, ID);
                    mv.visitVarInsn(ALOAD, index);


                    mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass,
                            RVConfig.instance.LOG_UNLOCK_INSTANCE,
                            RVConfig.instance.DESC_LOG_UNLOCK_INSTANCE);
                    // Comment out because of MCR unlock
                    // mv.visitInsn(opcode);
                    break;
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                case ARETURN:
                case RETURN:
                case ATHROW:
                    //如果是返回或者是抛出异常操作
                    //如果是thread的start方法之类的，就进行退出线程操作
                    onMethodExitNew();
                    mv.visitInsn(opcode);
                    break;
                default:
                    mv.visitInsn(opcode);
                    break;
                }

		}

	}

	/**
	 * Visits the end of the method. This method, which is the last one to be called, is used to inform the visitor that all the annotations and attributes of the method have been visited.
	 */
	@Override
	public void visitEnd() {
		mv.visitEnd();
	}

    // MultiNewArray 是jvm中创建数组的命令
	@Override
	public void visitMultiANewArrayInsn(String desc, int dims)
	{
	    mv.visitMultiANewArrayInsn(desc, dims);
	}
	
	
	@Override
	public void visitJumpInsn(int opcode, Label label) {
		String sig_loc = (className + "|" + methodSignature + "|" + line_cur).replace("/", ".");
		int ID = RVGlobalStateForInstrumentation.instance
				.getLocationId(sig_loc);
		//把所有branch的位置存到表中
		
//		System.out.println(sig_loc);

		switch (opcode) {
		case IFEQ:// branch
		case IFNE:
		case IFLT:
		case IFGE:
		case IFGT:
		case IFLE:
		case IF_ICMPEQ:
		case IF_ICMPNE:
		case IF_ICMPLT:
		case IF_ICMPGE:
		case IF_ICMPGT:
		case IF_ICMPLE:
		case IF_ACMPEQ:
		case IF_ACMPNE:
		case IFNULL:
		case IFNONNULL:
		    
		default:
			mv.visitJumpInsn(opcode, label);
			break;
		}
	}

	protected void onMethodEnter() {
		onMethodEnterNew();
	}

	protected void onMethodEnterNew() {
	    // 如果是synchronize的方法
        if(isSynchronized)
        {
            String sig_loc = source + "|" + (className+"|"+methodSignature+"|"+line_cur).replace("/", ".");
            int ID  = RVGlobalStateForInstrumentation.instance.getLocationId(sig_loc);
//            System.out.println(sig_loc + "\t id: " + ID);
            addBipushInsn(mv,ID);

            if(isStatic)
            {
                //signature + line number、
                //静态方法，只需取出类名就可以了
                String sig_var = (className+".0").replace("/", ".");
                int SID = RVGlobalStateForInstrumentation.instance.getVariableId(sig_var);
                //将类名对应的SID推入栈中
                addBipushInsn(mv,SID);
                mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, RVConfig.instance.LOG_LOCK_STATIC,
                        RVConfig.instance.DESC_LOG_LOCK_STATIC);
            }
            else
            {
                //实例方法需要访问this变量的引用，将this引用取出
                mv.visitVarInsn(ALOAD, 0); //the this objectref
                //将RVRuntime中的方法插桩到代码中
                mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, RVConfig.instance.LOG_LOCK_INSTANCE,
                        RVConfig.instance.DESC_LOG_LOCK_INSTANCE);
            }
        }
        
        //如果是可能会运行的方法，就插入logThread*的方法
		if (this.possibleRunMethod) {
			visitThreadBeginEnd(true);
		}
	}

	//该方法是onMethodEnterNew的镜像方法
	protected void onMethodExitNew() {
        if(isSynchronized)
        {
            String sig_loc = source + "|" + (className+"|"+methodSignature+"|"+line_cur).replace("/", ".");
            int ID  = RVGlobalStateForInstrumentation.instance.getLocationId(sig_loc);

            addBipushInsn(mv,ID);

            if(isStatic)
            {
                //signature + line number
                String sig_var = (className+".0").replace("/", ".");
                int SID = RVGlobalStateForInstrumentation.instance.getVariableId(sig_var);
                addBipushInsn(mv,SID);
                mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, RVConfig.instance.LOG_UNLOCK_STATIC,
                        RVConfig.instance.DESC_LOG_UNLOCK_STATIC);
            }
            else
            {
                mv.visitVarInsn(ALOAD, 0);//the this objectref
                mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, RVConfig.instance.LOG_UNLOCK_INSTANCE,
                        RVConfig.instance.DESC_LOG_UNLOCK_INSTANCE);
            }
        }

		if (this.possibleRunMethod) {
			visitThreadBeginEnd(false);
		}
	}

    //如果方法是thread.run等方法，那么就插入一段代码
	// if (this instanceof Runnable) {
	//   RVRuntime.logThreadBegin(); //或者是 logThreadEnd();
	// }
    private void visitThreadBeginEnd(boolean threadBegin) {
        super.mv.visitVarInsn(Opcodes.ALOAD, 0);
        super.mv.visitTypeInsn(Opcodes.INSTANCEOF, RVGlobalStateForInstrumentation.RUNNABLE_CLASS_NAME);
        Label l1 = new Label();
        super.mv.visitJumpInsn(Opcodes.IFEQ, l1);
        //super.updateThreadLocation();
        if (threadBegin) {
            mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, RVConfig.instance.LOG_THREAD_BEGIN, RVConfig.instance.DESC_LOG_THREAD_BEGIN);
        } else {
            mv.visitMethodInsn(INVOKESTATIC, RVInstrumentor.logClass, RVConfig.instance.LOG_THREAD_END, RVConfig.instance.DESC_LOG_THREAD_END);        }
        super.mv.visitLabel(l1);
    }
    
    /*
     * Private helper functions
     */

    /**
     * store the value to a variable
     * @param desc
     * @param index
     */
	private void storeValue(String desc, int index) {
		if (desc.startsWith("L") || desc.startsWith("[")) {
			mv.visitInsn(DUP);
			mv.visitVarInsn(ASTORE, index);
		} else if (desc.startsWith("I") || desc.startsWith("B")
				|| desc.startsWith("S") || desc.startsWith("Z")
				|| desc.startsWith("C")) {
			mv.visitInsn(DUP);         //duplicate the value on top of stack
			mv.visitVarInsn(ISTORE, index);  //store value to the #index variable
		} else if (desc.startsWith("J")) {
			mv.visitInsn(DUP2);
			mv.visitVarInsn(LSTORE, index);
			maxindex_cur++;
		} else if (desc.startsWith("F")) {
			mv.visitInsn(DUP);
			mv.visitVarInsn(FSTORE, index);
		} else if (desc.startsWith("D")) {
			mv.visitInsn(DUP2);
			mv.visitVarInsn(DSTORE, index);
			maxindex_cur++;
		}

		// if(classname.equals("org/eclipse/core/runtime/internal/adaptor/PluginConverterImpl"))
		// System.out.println("Signature: "+desc);
	}
	
	private void popValue(String desc, int index) {
        if (desc.startsWith("L") || desc.startsWith("[")) {
            //mv.visitInsn(DUP);
            mv.visitVarInsn(ASTORE, index);
        } else if (desc.startsWith("I") || desc.startsWith("B")
                || desc.startsWith("S") || desc.startsWith("Z")
                || desc.startsWith("C")) {
            //mv.visitInsn(DUP);         //duplicate the value on top of stack
            mv.visitVarInsn(ISTORE, index);  //store value to the #index variable
        } else if (desc.startsWith("J")) {
            //mv.visitInsn(DUP2);
            mv.visitVarInsn(LSTORE, index);
            maxindex_cur++;
        } else if (desc.startsWith("F")) {
            //mv.visitInsn(DUP);
            mv.visitVarInsn(FSTORE, index);
        } else if (desc.startsWith("D")) {
            //mv.visitInsn(DUP2);
            mv.visitVarInsn(DSTORE, index);
            maxindex_cur++;
        }

        // if(classname.equals("org/eclipse/core/runtime/internal/adaptor/PluginConverterImpl"))
        // System.out.println("Signature: "+desc);
    }

	private void loadValue(String desc, int index) {
		if (desc.startsWith("L") || desc.startsWith("[")) {
			mv.visitVarInsn(ALOAD, index);
		} else if (desc.startsWith("I")) {
			// convert int to object?
			mv.visitVarInsn(ILOAD, index);
			mv.visitMethodInsn(INVOKESTATIC, CLASS_INTEGER, METHOD_VALUEOF,
					DESC_INTEGER_VALUEOF);
		} else if (desc.startsWith("B")) {
			// convert int to object?
			mv.visitVarInsn(ILOAD, index);
			mv.visitMethodInsn(INVOKESTATIC, CLASS_BYTE, METHOD_VALUEOF,
					DESC_BYTE_VALUEOF);
		} else if (desc.startsWith("S")) {
			// convert int to object?
			mv.visitVarInsn(ILOAD, index);
			mv.visitMethodInsn(INVOKESTATIC, CLASS_SHORT, METHOD_VALUEOF,
					DESC_SHORT_VALUEOF);
		} else if (desc.startsWith("Z")) {
			// convert int to object?
			mv.visitVarInsn(ILOAD, index);
			mv.visitMethodInsn(INVOKESTATIC, CLASS_BOOLEAN, METHOD_VALUEOF,
					DESC_BOOLEAN_VALUEOF);
		} else if (desc.startsWith("C")) {
			// convert int to object?
			mv.visitVarInsn(ILOAD, index);
			mv.visitMethodInsn(INVOKESTATIC, CLASS_CHAR, METHOD_VALUEOF,
					DESC_CHAR_VALUEOF);
		} else if (desc.startsWith("J")) {
			// convert int to object?
			mv.visitVarInsn(LLOAD, index);
			mv.visitMethodInsn(INVOKESTATIC, CLASS_LONG, METHOD_VALUEOF,
					DESC_LONG_VALUEOF);
		} else if (desc.startsWith("F")) {
			// convert int to object?
			mv.visitVarInsn(FLOAD, index);
			mv.visitMethodInsn(INVOKESTATIC, CLASS_FLOAT, METHOD_VALUEOF,
					DESC_FLOAT_VALUEOF);
		} else if (desc.startsWith("D")) {
			// convert int to object?
			mv.visitVarInsn(DLOAD, index);
			mv.visitMethodInsn(INVOKESTATIC, CLASS_DOUBLE, METHOD_VALUEOF,
					DESC_DOUBLE_VALUEOF);
		}
	}

    // 这个方法不知道是干嘛的
	//TODO 判断这个方法的作用
	// 初步判断是将方法调用的id或者是域访问的id存常量表中，以供之后的方法使用
	private void addBipushInsn(MethodVisitor mv, int val) {
		switch (val) {
		case 0:
			mv.visitInsn(ICONST_0);
			break;
		case 1:
			mv.visitInsn(ICONST_1);
			break;
		case 2:
			mv.visitInsn(ICONST_2);
			break;
		case 3:
			mv.visitInsn(ICONST_3);
			break;
		case 4:
			mv.visitInsn(ICONST_4);
			break;
		case 5:
			mv.visitInsn(ICONST_5);
			break;
		default:
			mv.visitLdcInsn(new Integer(val));
			break;
		}
	}

	//将基本变量转为响应的对象类
	private void convertPrimitiveToObject(int opcode) {
		switch (opcode) {
		case IALOAD:
		case IASTORE:
			mv.visitMethodInsn(INVOKESTATIC, CLASS_INTEGER, METHOD_VALUEOF,
					DESC_INTEGER_VALUEOF);
			break;
		case BALOAD:
		case BASTORE:
			mv.visitMethodInsn(INVOKESTATIC, CLASS_BOOLEAN, METHOD_VALUEOF,
					DESC_BOOLEAN_VALUEOF);
			break;
		case CALOAD:
		case CASTORE:
			mv.visitMethodInsn(INVOKESTATIC, CLASS_CHAR, METHOD_VALUEOF,
					DESC_CHAR_VALUEOF);
			break;
		case DALOAD:
		case DASTORE:
			mv.visitMethodInsn(INVOKESTATIC, CLASS_DOUBLE, METHOD_VALUEOF,
					DESC_DOUBLE_VALUEOF);
			break;
		case FALOAD:
		case FASTORE:
			mv.visitMethodInsn(INVOKESTATIC, CLASS_FLOAT, METHOD_VALUEOF,
					DESC_FLOAT_VALUEOF);
			break;
		case LALOAD:
		case LASTORE:
			mv.visitMethodInsn(INVOKESTATIC, CLASS_LONG, METHOD_VALUEOF,
					DESC_LONG_VALUEOF);
			break;
		case SALOAD:
		case SASTORE:
			mv.visitMethodInsn(INVOKESTATIC, CLASS_SHORT, METHOD_VALUEOF,
					DESC_SHORT_VALUEOF);
			break;
		}
	}

}
